#!/bin/bash

source yaxim.rdf.sh
envsubst < base.rdf.xml > yaxim.rdf.xml

source bruno.rdf.sh
envsubst < base.rdf.xml > bruno.rdf.xml
