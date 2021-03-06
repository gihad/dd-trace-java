package datadog.trace

import datadog.opentracing.DDTracer
import datadog.trace.common.DDTraceConfig
import datadog.trace.common.sampling.AllSampler
import datadog.trace.common.writer.DDAgentWriter
import datadog.trace.common.writer.ListWriter
import datadog.trace.common.writer.LoggingWriter
import spock.lang.Specification
import spock.lang.Unroll

import java.lang.reflect.Field
import java.lang.reflect.Modifier

import static datadog.trace.common.DDTraceConfig.*

class DDTraceConfigTest extends Specification {
  static originalEnvMap
  static overrideEnvMap = new HashMap<String, String>()

  def setupSpec() {
    def envMapField = ProcessEnvironment.getDeclaredField("theUnmodifiableEnvironment")
    envMapField.setAccessible(true)

    Field modifiersField = Field.getDeclaredField("modifiers")
    modifiersField.setAccessible(true)
    modifiersField.setInt(envMapField, envMapField.getModifiers() & ~Modifier.FINAL)

    originalEnvMap = envMapField.get(null)
    overrideEnvMap.putAll(originalEnvMap)
    envMapField.set(null, overrideEnvMap)
  }

  def cleanupSpec() {
    def envMapField = ProcessEnvironment.getDeclaredField("theUnmodifiableEnvironment")
    envMapField.setAccessible(true)

    Field modifiersField = Field.getDeclaredField("modifiers")
    modifiersField.setAccessible(true)
    modifiersField.setInt(envMapField, envMapField.getModifiers() & ~Modifier.FINAL)

    originalEnvMap = envMapField.get(null)
    envMapField.set(null, originalEnvMap)
  }

  def setup() {
    overrideEnvMap.clear()
    overrideEnvMap.putAll(originalEnvMap)

    System.clearProperty(PREFIX + SERVICE_NAME)
    System.clearProperty(PREFIX + WRITER_TYPE)
    System.clearProperty(PREFIX + AGENT_HOST)
    System.clearProperty(PREFIX + AGENT_PORT)
  }

  def "verify env override"() {
    setup:
    overrideEnvMap.put("SOME_RANDOM_ENTRY", "asdf")

    expect:
    System.getenv("SOME_RANDOM_ENTRY") == "asdf"
  }

  def "verify defaults"() {
    when:
    def config = new DDTraceConfig()

    then:
    config.getProperty(SERVICE_NAME) == "unnamed-java-app"
    config.getProperty(WRITER_TYPE) == "DDAgentWriter"
    config.getProperty(AGENT_HOST) == "localhost"
    config.getProperty(AGENT_PORT) == "8126"

    when:
    config = new DDTraceConfig("A different service name")

    then:
    config.getProperty(SERVICE_NAME) == "A different service name"
    config.getProperty(WRITER_TYPE) == "DDAgentWriter"
    config.getProperty(AGENT_HOST) == "localhost"
    config.getProperty(AGENT_PORT) == "8126"
  }

  def "specify overrides via system properties"() {
    when:
    System.setProperty(PREFIX + SERVICE_NAME, "something else")
    System.setProperty(PREFIX + WRITER_TYPE, LoggingWriter.simpleName)
    def tracer = new DDTracer()

    then:
    tracer.serviceName == "something else"
    tracer.writer instanceof LoggingWriter
  }

  def "specify overrides via env vars"() {
    when:
    overrideEnvMap.put(propToEnvName(PREFIX + SERVICE_NAME), "still something else")
    overrideEnvMap.put(propToEnvName(PREFIX + WRITER_TYPE), LoggingWriter.simpleName)
    def tracer = new DDTracer()

    then:
    tracer.serviceName == "still something else"
    tracer.writer instanceof LoggingWriter
  }

  def "sys props override env vars"() {
    when:
    overrideEnvMap.put(propToEnvName(PREFIX + SERVICE_NAME), "still something else")
    overrideEnvMap.put(propToEnvName(PREFIX + WRITER_TYPE), ListWriter.simpleName)

    System.setProperty(PREFIX + SERVICE_NAME, "what we actually want")
    System.setProperty(PREFIX + WRITER_TYPE, DDAgentWriter.simpleName)
    System.setProperty(PREFIX + AGENT_HOST, "somewhere")
    System.setProperty(PREFIX + AGENT_PORT, "9999")

    def tracer = new DDTracer()

    then:
    tracer.serviceName == "what we actually want"
    tracer.writer.toString() == "DDAgentWriter { api=DDApi { tracesEndpoint=http://somewhere:9999/v0.3/traces } }"
  }

  def "verify defaults on tracer"() {
    when:
    def tracer = new DDTracer()

    then:
    tracer.serviceName == "unnamed-java-app"
    tracer.sampler instanceof AllSampler
    tracer.writer.toString() == "DDAgentWriter { api=DDApi { tracesEndpoint=http://localhost:8126/v0.3/traces } }"

    tracer.spanContextDecorators.size() == 6
  }

  @Unroll
  def "verify single override on #source for #key"() {
    when:
    System.setProperty(PREFIX + key, value)
    def tracer = new DDTracer()

    then:
    tracer."$source".toString() == expected

    where:

    source    | key            | value           | expected
    "writer"  | "default"      | "default"       | "DDAgentWriter { api=DDApi { tracesEndpoint=http://localhost:8126/v0.3/traces } }"
    "writer"  | "writer.type"  | "LoggingWriter" | "LoggingWriter { }"
    "writer"  | "agent.host"   | "somethingelse" | "DDAgentWriter { api=DDApi { tracesEndpoint=http://somethingelse:8126/v0.3/traces } }"
    "writer"  | "agent.port"   | "9999"          | "DDAgentWriter { api=DDApi { tracesEndpoint=http://localhost:9999/v0.3/traces } }"
  }
}
