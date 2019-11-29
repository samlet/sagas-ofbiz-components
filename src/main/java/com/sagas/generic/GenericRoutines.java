package com.sagas.generic;

import com.google.common.collect.Maps;
import com.sagas.hybrid.MetaBroker;
import com.sagas.meta.model.*;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.apache.ofbiz.entity.GenericEntityException;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Server that manages startup/shutdown of a {@code Greeter} server.
 */
@Singleton
public class GenericRoutines {
    private static final Logger logger = Logger.getLogger(GenericRoutines.class.getName());

    private Server server;
    @Inject
    Provider<SysInfoImpl> sysInfoProvider;
    @Inject
    Provider<EntityServantImpl> entityServantProvider;

    public void start() throws IOException {
        /* The port on which the server should run */
        int port = 50051;
        server = ServerBuilder.forPort(port)
                .addService(sysInfoProvider.get())
                .addService(entityServantProvider.get())
                .build()
                .start();
        logger.info(".. Generic routines (grpc) started, listening on " + port);
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                // Use stderr here since the logger may have been reset by its JVM shutdown hook.
                System.err.println("*** shutting down gRPC server since JVM is shutting down");
                GenericRoutines.this.stop();
                System.err.println("*** server shut down");
            }
        });
    }

    public void stop() {
        if (server != null) {
            server.shutdown();
        }
    }

    /**
     * Await termination on the main thread since the grpc library uses daemon threads.
     */
    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    public static class SysInfoImpl extends SysInfoGrpc.SysInfoImplBase {
        @Inject
        Provider<MetaBroker> metaBroker;

        @Override
        public void getSysInfo(InfoQuery request, StreamObserver<InfoMap> responseObserver) {
            InfoMap.Builder infoMap=InfoMap.newBuilder();
            Map<String,String> ctx= Maps.newHashMap();
            ctx.put("timestamp", String.valueOf(System.currentTimeMillis()));
            infoMap.putAllInfo(ctx);
            responseObserver.onNext(infoMap.build());
            responseObserver.onCompleted();
        }

        @Override
        public void queryMeta(MetaQuery request, StreamObserver<MetaPayload> responseObserver) {
            MetaPayload.Builder payload=metaBroker.get().execute(request);
            responseObserver.onNext(payload.build());
            responseObserver.onCompleted();
        }

        /*
        @Override
        public void sayHello(HelloRequest req, StreamObserver<HelloReply> responseObserver) {
            HelloReply reply = HelloReply.newBuilder().setMessage("Hello " + req.getName()).build();
            responseObserver.onNext(reply);
            responseObserver.onCompleted();
        }
        */
    }

    public static class EntityServantImpl extends EntityServantGrpc.EntityServantImplBase{
        @Inject
        EntityRoutines routines;

        @Override
        public void getEntityNames(InfoQuery request, StreamObserver<Names> responseObserver) {
            /*
            Names.Builder names=Names.newBuilder();
            names.addName("Test");
            responseObserver.onNext(names.build());
            */
            try {
                responseObserver.onNext(routines.getEntityNames());
                responseObserver.onCompleted();
            } catch (GenericEntityException e) {
                logger.warning(e.getMessage());
                responseObserver.onError(e);
            }
        }

        @Override
        public void storeAll(TaStringEntriesBatch request, StreamObserver<ModifyInfo> responseObserver) {
            try {
                int result=routines.storeAll(request);
                ModifyInfo info=ModifyInfo.newBuilder().setTotal(result).build();
                responseObserver.onNext(info);
                responseObserver.onCompleted();
            } catch (GenericEntityException e) {
                logger.warning(e.getMessage());
                ModifyInfo info=ModifyInfo.newBuilder()
                        .setTotal(-1)
                        .setMessage(e.getMessage()).build();
                responseObserver.onNext(info);
                responseObserver.onCompleted();
            }
        }
    }
}