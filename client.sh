#!/bin/bash
if [ $# -lt 1 ]; 
   then 
   printf "Not enough arguments - %d\n" $# 
   exit 0 
   fi 

case "$1" in

    enqueue)
	java -cp .:./*:./lib/* -Dlog4j.configuration=log4j.local.properties  com.pinterest.pinlater.client.PinLaterClientTool --host localhost --port 9010 --mode enqueue --queue $2 --num_queries 1000 --batch_size 1 --concurrency 1
    ;;

    job-count)
    java -cp .:./*:./lib/* -Dlog4j.configuration=log4j.local.properties com.pinterest.pinlater.client.PinLaterClientTool --host localhost --port 9010 --mode get_job_count --queue $2 --job_state $3 --priority $4 --count_future_jobs false
    ;;

    lookup-job)
    java -cp .:./*:./lib/* -Dlog4j.configuration=log4j.local.properties com.pinterest.pinlater.client.PinLaterClientTool --host localhost --port 9010 --mode lookup --queue $2 --job_descriptor $3
    ;;

    create)
    java -cp .:./*:./lib/* -Dlog4j.configuration=log4j.local.properties com.pinterest.pinlater.client.PinLaterClientTool --host localhost --port 9010 --mode create  --queue $2
    ;;

esac