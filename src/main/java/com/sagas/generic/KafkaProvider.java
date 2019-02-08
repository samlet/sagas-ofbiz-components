package com.sagas.generic;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;

import javax.inject.Singleton;
import java.util.Properties;

@Singleton
public class KafkaProvider {
    private org.apache.kafka.clients.producer.Producer producer;
    public KafkaProvider(){
        //Configure the Producer
        Properties configProperties = new Properties();
        configProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,"localhost:9092");
        configProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,"org.apache.kafka.common.serialization.ByteArraySerializer");
        configProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,"org.apache.kafka.common.serialization.StringSerializer");

        this.producer = new KafkaProducer(configProperties);
    }

    public void post(String topicName, String line){
        ProducerRecord<String, String> rec = new ProducerRecord<String, String>(topicName,line);
        producer.send(rec);
    }

    public void stop(){
        producer.close();
    }
}
