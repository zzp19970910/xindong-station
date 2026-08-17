package com.xindong.incentive.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.action.Action;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.EnumStateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineConfigurationConfigurer;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;
import org.springframework.statemachine.listener.StateMachineListenerAdapter;
import org.springframework.statemachine.transition.Transition;

import java.util.EnumSet;

@Slf4j
@Configuration
@EnableStateMachineFactory
@RequiredArgsConstructor
public class WishStateMachineConfig extends EnumStateMachineConfigurerAdapter<WishState, WishEvent> {

    @Override
    public void configure(StateMachineConfigurationConfigurer<WishState, WishEvent> config) throws Exception {
        config
                .withConfiguration()
                .autoStartup(true)
                .listener(new StateMachineListenerAdapter<>() {
                    @Override
                    public void transition(Transition<WishState, WishEvent> transition) {
                        if (transition.getSource() != null) {
                            log.info("[愿望状态机] {} -> {} / event: {}",
                                    transition.getSource().getId(),
                                    transition.getTarget().getId(),
                                    transition.getTrigger() != null ? transition.getTrigger().getEvent() : "INIT");
                        }
                    }
                });
    }

    @Override
    public void configure(StateMachineStateConfigurer<WishState, WishEvent> states) throws Exception {
        states
                .withStates()
                .initial(WishState.DRAFT)
                .states(EnumSet.allOf(WishState.class))
                .end(WishState.COMPLETED);
    }

    @Override
    public void configure(StateMachineTransitionConfigurer<WishState, WishEvent> transitions) throws Exception {
        transitions
                .withExternal().source(WishState.DRAFT).target(WishState.PENDING_APPROVAL).event(WishEvent.APPLY).action(auditAction())
                .and()
                .withExternal().source(WishState.PENDING_APPROVAL).target(WishState.APPROVED).event(WishEvent.APPROVE).action(auditAction())
                .and()
                .withExternal().source(WishState.PENDING_APPROVAL).target(WishState.DRAFT).event(WishEvent.REJECT).action(auditAction())
                .and()
                .withExternal().source(WishState.APPROVED).target(WishState.COMPLETED).event(WishEvent.COMPLETE).action(auditAction())
                .and()
                .withExternal().source(WishState.PENDING_APPROVAL).target(WishState.DRAFT).event(WishEvent.CANCEL).action(auditAction())
                .and()
                .withExternal().source(WishState.APPROVED).target(WishState.DRAFT).event(WishEvent.ROLLBACK).action(auditAction());
    }

    @Bean
    public Action<WishState, WishEvent> auditAction() {
        return ctx -> {
            Object wishId = ctx.getMessageHeaders().get("wishId");
            Object coupleId = ctx.getMessageHeaders().get("coupleId");
            Object operatorId = ctx.getMessageHeaders().get("operatorId");
            WishState from = ctx.getTransition().getSource() != null ? ctx.getTransition().getSource().getId() : null;
            WishState to = ctx.getTransition().getTarget().getId();
            log.info("[状态机审计] wishId={}, coupleId={}, operatorId={}, {} -> {}",
                    wishId, coupleId, operatorId, from, to);
        };
    }
}