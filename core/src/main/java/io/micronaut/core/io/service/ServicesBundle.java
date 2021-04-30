package io.micronaut.core.io.service;

import java.util.Collection;

public interface ServicesBundle {

    void populate(Collection<Object> services);

}
