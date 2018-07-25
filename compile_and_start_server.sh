#!/bin/sh
rm -rf install/*

mvn clean compile

mvn clean package

tar -zxvf target/pinlater-0.1-SNAPSHOT-bin.tar.gz -C install

cd install

mkdir log

open -a Terminal "`pwd`"

java -server -cp .:./*:./lib/* -Dserver_config=pinlater.redis.local.properties -Dbackend_config=redis.local.json -Dlog4j.configuration=log4j.local.properties com.pinterest.pinlater.PinLaterServer