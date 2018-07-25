package com.pinterest.pinlater.job;

import com.pinterest.pinlater.thrift.PinLaterJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

/**
 * An example PinLater job
 */
public class FailedJob {

  private static final Logger LOG = LoggerFactory.getLogger(FailedJob.class);
  public static final String QUEUE_NAME = "print_queue";

  private String logData;

  public FailedJob(String logData) {
    this.logData = logData;
  }

  /**
   * Build a PinLaterJob object that can be used to build an enqueue request.
   */
  public PinLaterJob buildJob() {
    PinLaterJob job = new PinLaterJob(ByteBuffer.wrap(logData.getBytes()));
    job.setNumAttemptsAllowed(10);
    return job;
  }

  public static void process(String logData) throws Exception{
	LOG.info("FailedJob: {}", "To throw exception: " + logData);
	throw new RuntimeException("Exception thrown to test job failure");
  }
}
