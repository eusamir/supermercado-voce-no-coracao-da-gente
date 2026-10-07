package com.vocenocoracao.supermercado_api.config;

import static com.vocenocoracao.supermercado_api.payment.messaging.PaymentMessaging.APPROVED_QUEUE;
import static com.vocenocoracao.supermercado_api.payment.messaging.PaymentMessaging.APPROVED_ROUTING_KEY;
import static com.vocenocoracao.supermercado_api.payment.messaging.PaymentMessaging.DEAD_LETTER_EXCHANGE;
import static com.vocenocoracao.supermercado_api.payment.messaging.PaymentMessaging.DEAD_LETTER_QUEUE;
import static com.vocenocoracao.supermercado_api.payment.messaging.PaymentMessaging.DEAD_LETTER_ROUTING_KEY;
import static com.vocenocoracao.supermercado_api.payment.messaging.PaymentMessaging.EXCHANGE;
import static com.vocenocoracao.supermercado_api.payment.messaging.PaymentMessaging.REQUESTED_QUEUE;
import static com.vocenocoracao.supermercado_api.payment.messaging.PaymentMessaging.REQUESTED_ROUTING_KEY;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Bean
    public DirectExchange paymentExchange() {
        return new DirectExchange(EXCHANGE);
    }

    @Bean
    public DirectExchange paymentDeadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE);
    }

    @Bean
    public Queue paymentRequestedQueue() {
        return QueueBuilder.durable(REQUESTED_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue paymentApprovedQueue() {
        return QueueBuilder.durable(APPROVED_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue paymentDeadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding paymentRequestedBinding() {
        return BindingBuilder.bind(paymentRequestedQueue()).to(paymentExchange()).with(REQUESTED_ROUTING_KEY);
    }

    @Bean
    public Binding paymentApprovedBinding() {
        return BindingBuilder.bind(paymentApprovedQueue()).to(paymentExchange()).with(APPROVED_ROUTING_KEY);
    }

    @Bean
    public Binding paymentDeadLetterBinding() {
        return BindingBuilder.bind(paymentDeadLetterQueue())
                .to(paymentDeadLetterExchange())
                .with(DEAD_LETTER_ROUTING_KEY);
    }

    @Bean
    public MessageConverter messageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}
