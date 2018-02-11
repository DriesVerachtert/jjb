#!/bin/bash
set -e
set -x

. /opt/rh/rh-python36/enable
. ./jenkins-job-builder/.venv/bin/activate
jenkins-jobs test .
