#!/bin/sh

sbt ++2.11.7 \
  clean \
  micro/publishSigned \
  microJson/publishSigned \
  microScalate/publishSigned \
  microServer/publishSigned \
  microTest/publishSigned \
  ++2.10.5 \
  clean \
  micro/publishSigned \
  microJson/publishSigned \
  microScalate/publishSigned \
  microServer/publishSigned \
  microTest/publishSigned
