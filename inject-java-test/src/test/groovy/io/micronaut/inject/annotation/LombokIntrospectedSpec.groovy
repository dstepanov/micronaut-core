package io.micronaut.inject.annotation

import io.micronaut.annotation.processing.test.LombokIntrospectedClass
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.type.Argument
import spock.lang.Specification

import javax.inject.Named

class LombokIntrospectedSpec extends Specification {

    def "should have correct constructor argument names and annotations"() {
        when:
            Argument<?>[] arguments = BeanIntrospection
                .getIntrospection(LombokIntrospectedClass.class)
                .getConstructorArguments()

        then:
            arguments[0].getName() == "field1"
            arguments[1].getName() == "field2"
            arguments[2].getName() == "field3"
            arguments[3].getName() == "nameXyz"
            arguments[3].getAnnotation(Named.class).stringValue().get() == "abc"
    }

}
