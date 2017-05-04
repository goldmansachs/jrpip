#!/bin/sh

# uncomment next line and set to your local jdk
#export JRPIP_JDK_HOME=/opt/jdk1.6.0_45

# no need to modify stuff below:

#long winded way to find the script directory; works on OSX
PRG="$0"
while [ -h "$PRG" ]; do
  ls=`ls -ld "$PRG"`
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '.*/.*' > /dev/null; then
    PRG="$link"
  else
    PRG=`dirname "$PRG"`/"$link"
  fi
done
SCRIPTPATH=$(dirname "$PRG")

SCRIPTPATH=`cd "$SCRIPTPATH" && pwd`

export JRPIP_HOME=${JRPIP_HOME:-"$SCRIPTPATH/.."}

echo JRPIP_HOME is $JRPIP_HOME

export JDK_HOME=${JRPIP_JDK_HOME:-"/Library/Java/JavaVirtualMachines/1.6.0.jdk/Contents/Home"}

export GENERATE_JRPIP_CONCRETE_CLASSES=true

#  Copyright 2017 Goldman Sachs.
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing,
#  software distributed under the License is distributed on an
#  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
#  KIND, either express or implied.  See the License for the
#  specific language governing permissions and limitations
#  under the License.
