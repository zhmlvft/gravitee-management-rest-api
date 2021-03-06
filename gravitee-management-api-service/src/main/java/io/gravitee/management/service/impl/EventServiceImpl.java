/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.management.service.impl;

import io.gravitee.common.data.domain.Page;
import io.gravitee.management.model.EventEntity;
import io.gravitee.management.model.EventType;
import io.gravitee.management.model.NewEventEntity;
import io.gravitee.management.service.EventService;
import io.gravitee.management.service.exceptions.EventNotFoundException;
import io.gravitee.management.service.exceptions.TechnicalManagementException;
import io.gravitee.repository.exceptions.TechnicalException;
import io.gravitee.repository.management.api.EventRepository;
import io.gravitee.repository.management.api.search.EventCriteria;
import io.gravitee.repository.management.api.search.builder.PageableBuilder;
import io.gravitee.repository.management.model.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE
 */
@Component
public class EventServiceImpl extends TransactionalService implements EventService {

    private final Logger LOGGER = LoggerFactory.getLogger(EventServiceImpl.class);

    @Autowired
    private EventRepository eventRepository;

    @Override
    public EventEntity findById(String id) {
        try {
            LOGGER.debug("Find event by ID: {}", id);

            Optional<Event> event = eventRepository.findById(id);

            if (event.isPresent()) {
                return convert(event.get());
            }

            throw new EventNotFoundException(id);
        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to find an event using its ID {}", id, ex);
            throw new TechnicalManagementException("An error occurs while trying to find an event using its ID " + id, ex);
        }
    }

    @Override
    public EventEntity create(NewEventEntity newEventEntity) {
        String hostAddress = "";
        try {
            hostAddress = InetAddress.getLocalHost().getHostAddress();
            LOGGER.debug("Create {} for server {}", newEventEntity, hostAddress);

            Event event = convert(newEventEntity);
            event.setId(io.gravitee.common.utils.UUID.random().toString());

            // Set origin
            event.getProperties().put(Event.EventProperties.ORIGIN.getValue(), hostAddress);
            // Set date fields
            event.setCreatedAt(new Date());
            event.setUpdatedAt(event.getCreatedAt());

            Event createdEvent = eventRepository.create(event);

            return convert(createdEvent);
        } catch (UnknownHostException e) {
            LOGGER.error("An error occurs while getting the server IP address", e);
            throw new TechnicalManagementException("An error occurs while getting the server IP address", e);
        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to create {} for server {}", newEventEntity, hostAddress, ex);
            throw new TechnicalManagementException("An error occurs while trying create " + newEventEntity + " for server " + hostAddress, ex);
        }
    }

    @Override
    public EventEntity create(EventType type, String payload, Map<String, String> properties) {
        NewEventEntity event = new NewEventEntity();
        event.setType(type);
        event.setPayload(payload);
        event.setProperties(properties);
        return create(event);
    }
    
    @Override
    public void delete(String eventId) {
        try {
            LOGGER.debug("Delete Event {}", eventId);
            eventRepository.delete(eventId);
        } catch (TechnicalException ex) {
            LOGGER.error("An error occurs while trying to delete Event {}", eventId, ex);
            throw new TechnicalManagementException("An error occurs while trying to delete Event " + eventId, ex);
        }
    }

    @Override
    public Set<EventEntity> findByType(List<EventType> eventTypes) {
        io.gravitee.repository.management.model.EventType[] eventTypesArr =  eventTypes.stream()
                .map(eventType -> io.gravitee.repository.management.model.EventType.valueOf(eventType.toString()))
                .toArray(io.gravitee.repository.management.model.EventType[]::new);

        return convert(eventRepository.search(new EventCriteria.Builder().types(eventTypesArr).build()));
    }

    @Override
    public Set<EventEntity> findByApi(String apiId) {
        return findByProperty(Event.EventProperties.API_ID.getValue(), apiId);
    }

    @Override
    public Set<EventEntity> findByUser(String username) {
        return findByProperty(Event.EventProperties.USERNAME.getValue(), username);
    }

    @Override
    public Set<EventEntity> findByOrigin(String origin) {
        return findByProperty(Event.EventProperties.ORIGIN.getValue(), origin);
    }

    private Set<EventEntity> findByProperty(String property, String value) {
        return convert(eventRepository.search(new EventCriteria.Builder().property(property, value).build()));
    }

    @Override
    public Page<EventEntity> search(List<EventType> eventTypes,
                                    Map<String, Object> properties, long from, long to, int page, int size) {
        EventCriteria.Builder builder = new EventCriteria.Builder().from(from).to(to);

        if (eventTypes != null) {
            io.gravitee.repository.management.model.EventType[] eventTypesArr = eventTypes.stream()
                    .map(eventType -> io.gravitee.repository.management.model.EventType.valueOf(eventType.toString()))
                    .toArray(io.gravitee.repository.management.model.EventType[]::new);

            builder.types(eventTypesArr);
        }

        if (properties != null) {
            properties.forEach(builder::property);
        }

        Page<Event> pageEvent = eventRepository.search(
                builder.build(),
                new PageableBuilder().pageNumber(page).pageSize(size).build());

        List<EventEntity> content = pageEvent.getContent().stream().map(this::convert).collect(Collectors.toList());

        return new Page<>(content, page, size, pageEvent.getTotalElements());
    }

    private Set<EventEntity> convert(List<Event> events) {
        return events.stream().map(this::convert).collect(Collectors.toSet());
    }

    private EventEntity convert(Event event) {
        EventEntity eventEntity = new EventEntity();
        eventEntity.setId(event.getId());
        eventEntity.setType(io.gravitee.management.model.EventType.valueOf(event.getType().toString()));
        eventEntity.setPayload(event.getPayload());
        eventEntity.setParentId(event.getParentId());
        eventEntity.setProperties(event.getProperties());
        eventEntity.setCreatedAt(event.getCreatedAt());
        eventEntity.setUpdatedAt(event.getUpdatedAt());

        return eventEntity;
    }

    private Event convert(NewEventEntity newEventEntity) {
        Event event = new Event();
        event.setType(io.gravitee.repository.management.model.EventType.valueOf(newEventEntity.getType().toString()));
        event.setPayload(newEventEntity.getPayload());
        event.setParentId(newEventEntity.getParentId());
        event.setProperties(newEventEntity.getProperties());

        return event;
    }
}
