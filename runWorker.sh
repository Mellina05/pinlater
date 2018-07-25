#!/bin/sh
 java -server -cp .:./*:./lib/* -Dserverset_path=discovery.pinlater.local -Dlog4j.configuration=log4j.local.properties -Dqueue=print_queue com.pinterest.pinlater.worker.Worker