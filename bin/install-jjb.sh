#!/bin/bash
set -e
set -x

# sudo yum install rh-python36-python-virtualenv.noarch
# rm -Rf jenkins-job-builder

. /opt/rh/rh-python36/enable
git clone https://git.openstack.org/openstack-infra/jenkins-job-builder
cd jenkins-job-builder
virtualenv .venv
source .venv/bin/activate
pip install -r test-requirements.txt -e .
