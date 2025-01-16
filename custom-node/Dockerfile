FROM gcr.io/hedera-registry/consensus-node:0.64.0
RUN rm -rf /opt/hgcapp/services-hedera/HapiApp2.0/data/apps/*
RUN rm -rf /opt/hgcapp/services-hedera/HapiApp2.0/data/lib
COPY ../hedera-node/data/apps/HederaNode.jar /opt/hgcapp/services-hedera/HapiApp2.0/data/apps/HederaNode.jar
COPY ../hedera-node/data/lib /opt/hgcapp/services-hedera/HapiApp2.0/data/lib
