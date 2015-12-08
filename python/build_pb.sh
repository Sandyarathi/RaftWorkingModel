#!/bin/bash
#
# creates the python classes for our .proto
#
project_base="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# which protoc that you built
#PROTOC_HOME=/usr/local/protobuf-2.4.1/
PROTOC_HOME=/usr/local/Cellar/protobuf/2.6.1/

protoc -I=. --python_out=. comm.proto 
