#!/bin/bash

JRPIP_VERSION=4.0.0

echo Enter passphrase:
read -s PASSPHRASE

cd ../target
gpg --batch --yes --passphrase $PASSPHRASE -ab jrpip-$JRPIP_VERSION-javadoc.jar
gpg --batch --yes --passphrase $PASSPHRASE -ab jrpip-$JRPIP_VERSION-sources.jar
gpg --batch --yes --passphrase $PASSPHRASE -ab jrpip-$JRPIP_VERSION.jar
gpg --batch --yes --passphrase $PASSPHRASE -ab jrpip-$JRPIP_VERSION.pom
