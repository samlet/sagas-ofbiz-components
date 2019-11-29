package com.sagas.blueprints;

import akka.NotUsed;
import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.unmarshalling.StringUnmarshallers;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import akka.stream.javadsl.Flow;
import akka.util.Timeout;
import org.apache.ofbiz.base.container.Container;
import org.apache.ofbiz.base.container.ContainerException;
import org.apache.ofbiz.base.start.StartupCommand;
import scala.concurrent.duration.FiniteDuration;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static akka.pattern.PatternsCS.ask;

/*
When you run this server, you can add an auction bid via
curl -X PUT 'http://localhost:8090/auction?bid=22&user=MartinO'
on the terminal;
and then you can view the auction status either in a browser,
at the url http://localhost:8090/auction,
or, on the terminal, via
curl http://localhost:8090/auction.
 */

@Singleton
public class HttpServerActorInteraction extends AllDirectives /*implements Container*/ {

    private ActorRef auction;
    // private String configFile = null;
    private String containerName="httpActor";

    @Inject
    ActorSystem system;

    Materializer materializer;
    private Http http;
    private CompletionStage<ServerBinding> binding;

    public HttpServerActorInteraction() {
    }

    private Route createRoute() {
        return route(
                path("auction", () -> route(
                        put(() ->
                                parameter(StringUnmarshallers.INTEGER, "bid", bid ->
                                        parameter("user", user -> {
                                            // place a bid, fire-and-forget
                                            auction.tell(new Bid(user, bid), ActorRef.noSender());
                                            return complete(StatusCodes.ACCEPTED, "bid placed");
                                        })
                                )),
                        get(() -> {
                            final Timeout timeout = Timeout.durationToTimeout(FiniteDuration.apply(5, TimeUnit.SECONDS));
                            // query the actor for the current auction state
                            CompletionStage<Bids> bids = ask(auction, new GetBids(), timeout).thenApply((Bids.class::cast));
                            return completeOKWithFuture(bids, Jackson.marshaller());
                        }))));
    }

    /* @Override
    private void init(List<StartupCommand> ofbizCommands, String name, String configFile) throws ContainerException {
        this.containerName = name;
        this.configFile = configFile;
    }
    */

    // @Override
    public boolean start() {
        // boot up server using the route as defined below
        // system = ActorSystem.create("routes");

        http = Http.get(system);
        materializer = ActorMaterializer.create(system);

        //In order to access all directives we need an instance where the routes are define.
        // HttpServerActorInteraction app = new HttpServerActorInteraction(system);
        auction = system.actorOf(Auction.props(), this.containerName);

        final Flow<HttpRequest, HttpResponse, NotUsed> routeFlow = createRoute().flow(system, materializer);
        binding = http.bindAndHandle(routeFlow,
                // ConnectHttp.toHost("localhost", 8090), materializer);
                ConnectHttp.toHost("0.0.0.0", 8090), materializer);

        System.out.println(" [✔] Blueprints server online at http://0.0.0.0:8090...");
        return true;
    }

    // @Override
    public void stop() throws ContainerException {
        binding
                .thenCompose(ServerBinding::unbind) // trigger unbinding from the port
                .thenAccept(unbound -> system.terminate()); // and shutdown when done
    }

    // @Override
    public String getName() {
        return this.containerName;
    }

    static class Bid {
        public final String userId;
        public final int offer;

        public Bid(String userId, int offer) {
            this.userId = userId;
            this.offer = offer;
        }
    }

    static class GetBids {

    }

    static class Bids {
        public final List<Bid> bids;

        Bids(List<Bid> bids) {
            this.bids = bids;
        }
    }

    static class Auction extends AbstractActor {

        private final LoggingAdapter log = Logging.getLogger(context().system(), this);

        List<Bid> bids = new ArrayList<>();

        static Props props() {
            return Props.create(Auction.class);
        }

        @Override
        public Receive createReceive() {
            return receiveBuilder()
                    .match(HttpServerActorInteraction.Bid.class, bid -> {
                        bids.add(bid);
                        log.info("Bid complete: {}, {}", bid.userId, bid.offer);
                    })
                    .match(HttpServerActorInteraction.GetBids.class, m -> {
                        sender().tell(new HttpServerActorInteraction.Bids(bids), self());
                    })
                    .matchAny(o -> log.info("Invalid message"))
                    .build();
        }
    }
}