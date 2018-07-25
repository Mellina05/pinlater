package com.pinterest.pinlater.worker;

import com.pinterest.pinlater.client.PinLaterClient;
import com.pinterest.pinlater.commons.config.ConfigFileServerSet;
import com.pinterest.pinlater.commons.util.BytesUtil;
import com.pinterest.pinlater.job.PrintJob;
import com.pinterest.pinlater.thrift.PinLaterDequeueMetadata;
import com.pinterest.pinlater.thrift.PinLaterDequeueRequest;
import com.pinterest.pinlater.thrift.PinLaterDequeueResponse;
import com.pinterest.pinlater.thrift.PinLaterJobAckInfo;
import com.pinterest.pinlater.thrift.PinLaterJobAckRequest;
import com.pinterest.pinlater.thrift.RequestContext;
import com.twitter.common.zookeeper.ServerSet;
import com.twitter.util.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.runtime.BoxedUnit;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An example PinLater worker implementation.
 *
 * It uses two separate threads for dequeue and ACK, and a thread pool for executing the jobs.
 * Completed jobs will be pushed into two queues depending on whether the execution succeeded or
 * failed. Note that the dequeue thread can also send ACK along with the dequeue request. The
 * worker also implements a linear backoff retry policy, where the retry delay is calculated with
 * the number of retry allowed and remained.
 *
 * File-based serverset is used for service discovery. It uses a local file that stores the
 * servers' [HOST_IP]:[PORT] pairs instead of talking to Zookeeper directly.
 */
public class WorkerForPrintJob {

  private static final int DEQUEUE_BATCH_SIZE = 10;
  private static final int NUM_WORKER_THREADS = 10;
  private static final int DEQUEUE_INTEVAL_MS = 1000;
  private static final int ACK_INTEVAL_MS = 1000;
  private static final int PENDING_JOB_LIMIT = 50;

  private static final Logger LOG = LoggerFactory.getLogger(WorkerForPrintJob.class);
  private static final RequestContext REQUEST_CONTEXT;
  private static String QUEUE_NAME;
  static {
    try {
      REQUEST_CONTEXT = new RequestContext(
          "pinlaterexampleworker:" + InetAddress.getLocalHost().getHostName());
    } catch (UnknownHostException e) {
      LOG.error("Failed to initializer PinLaterExampleWorker", e);
      throw new RuntimeException(e);
    }
  }

  // Max number of pending/running jobs. The worker will stop dequeue when reaches this limit.
  private final AtomicInteger numPendingJobs = new AtomicInteger(0);
  // Thread pool for executing PinLater jobs.
  private final ExecutorService workerExecutor = Executors.newFixedThreadPool(NUM_WORKER_THREADS);

  // Local buffer for succeeded and failed jobs, waiting for ACK
  private final BlockingQueue<PinLaterJobAckInfo> succeededJobQueue =
      new LinkedBlockingQueue<PinLaterJobAckInfo>();
  private final BlockingQueue<PinLaterJobAckInfo> failedJobQueue =
      new LinkedBlockingQueue<PinLaterJobAckInfo>();

  private PinLaterClient client;

  public WorkerForPrintJob() {
    String fullServerSetPath =
        getClass().getResource("/" + System.getProperty("serverset_path")).getPath();
		QUEUE_NAME = System.getProperty("queue");
    ServerSet serverSet = new ConfigFileServerSet(fullServerSetPath);
    this.client = new PinLaterClient(serverSet, 10);

    ScheduledExecutorService dequeueAckExecutor = Executors.newScheduledThreadPool(2);
    dequeueAckExecutor.scheduleWithFixedDelay(
        new DequeueThread(), 0, DEQUEUE_INTEVAL_MS, TimeUnit.MILLISECONDS);
    dequeueAckExecutor.scheduleWithFixedDelay(
        new AckThread(), 0, ACK_INTEVAL_MS, TimeUnit.MILLISECONDS);
  }

  public static void main(String[] args) {
    new WorkerForPrintJob();
  }

  private PinLaterJobAckRequest buildAckRequest() {
    List<PinLaterJobAckInfo> succeededJobs = new ArrayList<PinLaterJobAckInfo>();
    List<PinLaterJobAckInfo> failedJobs = new ArrayList<PinLaterJobAckInfo>();
    succeededJobQueue.drainTo(succeededJobs);
    failedJobQueue.drainTo(failedJobs);
    if (succeededJobs.size() > 0 || failedJobs.size() > 0) {
      LOG.info("ACK {}: {} succeeded, {} failed", QUEUE_NAME,
          succeededJobs.size(), failedJobs.size());
      PinLaterJobAckRequest ackRequest =
          new PinLaterJobAckRequest(QUEUE_NAME);
      ackRequest.setJobsSucceeded(succeededJobs);
      ackRequest.setJobsFailed(failedJobs);
      return ackRequest;
    } else {
      return null;
    }
  }

  class DequeueThread implements Runnable {
    public void run() {
      if (numPendingJobs.get() > PENDING_JOB_LIMIT) {
        return;
      }

      PinLaterDequeueRequest dequeueRequest =
          new PinLaterDequeueRequest(QUEUE_NAME, DEQUEUE_BATCH_SIZE);

      // Ack completed jobs along with dequeue request
      PinLaterJobAckRequest ackRequest = buildAckRequest();
      if (ackRequest != null) {
        dequeueRequest.setJobAckRequest(ackRequest);
      }

      client.getIface().dequeueJobs(REQUEST_CONTEXT, dequeueRequest).onSuccess(
          new Function<PinLaterDequeueResponse, BoxedUnit>() {
            public BoxedUnit apply(final PinLaterDequeueResponse response) {
              LOG.info("DEQUEUE {}: {} jobs, {} jobs pending",
                  QUEUE_NAME, response.getJobsSize(), numPendingJobs.get());
              for (final Map.Entry<String, ByteBuffer> job : response.getJobs().entrySet()) {
                numPendingJobs.incrementAndGet();
                workerExecutor.submit(new Runnable() {
                  public void run() {
                    try {
                      PrintJob.process(
                          new String(BytesUtil.readBytesFromByteBuffer(job.getValue())));
                      succeededJobQueue.add(new PinLaterJobAckInfo(job.getKey()));
                    } catch (Exception e) {
                      PinLaterJobAckInfo ackInfo = new PinLaterJobAckInfo(job.getKey());

                      // Append exception message to the custom status
                      ackInfo.setAppendCustomStatus(e.getMessage());

                      // Retry with linear backoff, e.g. 1s, 2s, 3s ...
                      PinLaterDequeueMetadata metaData =
                          response.getJobMetadata().get(job.getKey());
                      int attemptsAllowed = metaData.getAttemptsAllowed();
                      int attemptsRemained = metaData.getAttemptsRemaining();
                      ackInfo.setRetryDelayMillis(1000 * (attemptsAllowed - attemptsRemained));

                      failedJobQueue.add(ackInfo);
                    } finally {
                      numPendingJobs.decrementAndGet();
                    }
                  }
                });
              }
              return BoxedUnit.UNIT;
            }
          }
      );
    }
  }

  class AckThread implements Runnable {
    public void run() {
      PinLaterJobAckRequest ackRequest = buildAckRequest();
      if (ackRequest != null) {
        client.getIface().ackDequeuedJobs(REQUEST_CONTEXT, ackRequest);
      }
    }
  }
}
