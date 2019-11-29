package com.sagas.blueprints;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import com.sagas.meta.model.BlueOffer;

import java.util.ArrayList;
import java.util.List;

public class SimpleActor extends AbstractActor {
    static class GetOffers {
    }
    static class Offers {
        public final List<BlueOffer> bids;

        Offers(List<BlueOffer> bids) {
            this.bids = bids;
        }
    }
    private final LoggingAdapter log = Logging.getLogger(context().system(), this);

    List<BlueOffer> bids = new ArrayList<>();

    static Props props() {
        return Props.create(BlueOffer.class);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(BlueOffer.class, bid -> {
                    bids.add(bid);
                    log.info("Offer complete: {}, {}", bid.getUser(), bid.getOffer());
                })
                .match(GetOffers.class, m -> {
                    sender().tell(new Offers(bids), self());
                })
                .matchAny(o -> log.info("Invalid message"))
                .build();
    }
}
