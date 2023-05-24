#!/bin/sh -eux

# Move to root folder of strimzi
cd ../../../../

eval $(minikube docker-env)

#run tests
export DOCKER_REGISTRY="$(kubectl get service registry -n kube-system -o=jsonpath='{.spec.clusterIP}'):80"
mvn verify -pl systemtest -P ${TEST_PROFILE} \
    -Dgroups="${TEST_GROUPS}" \
    -DexcludedGroups="loadbalancer" \
    -Dmaven.javadoc.skip=true \
    -Dfailsafe.rerunFailingTestsCount=1 \
    --no-transfer-progress
