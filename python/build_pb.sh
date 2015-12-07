#!/bin/bash
#
# creates the python classes for our .proto
#

protoc -I=. --python_out=. comm.proto 
