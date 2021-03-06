package io.vlingo.http.resource;

import io.vlingo.http.Header;
import io.vlingo.http.Method;
import io.vlingo.http.Request;
import io.vlingo.http.Response;

import java.util.Arrays;

public class RequestHandler4<T, R, U, I> extends RequestHandler {
  final private ParameterResolver<T> resolverParam1;
  final private ParameterResolver<R> resolverParam2;
  final private ParameterResolver<U> resolverParam3;
  final private ParameterResolver<I> resolverParam4;
  private Handler4<T, R, U, I> handler;

  RequestHandler4(final Method method,
                  final String path,
                  final ParameterResolver<T> resolverParam1,
                  final ParameterResolver<R> resolverParam2,
                  final ParameterResolver<U> resolverParam3,
                  final ParameterResolver<I> resolverParam4) {
    super(method, path, Arrays.asList(resolverParam1, resolverParam2, resolverParam3, resolverParam4));
    this.resolverParam1 = resolverParam1;
    this.resolverParam2 = resolverParam2;
    this.resolverParam3 = resolverParam3;
    this.resolverParam4 = resolverParam4;
  }

  @FunctionalInterface
  public interface Handler4<T, R, U, I> {
    Response execute(T param1, R param2, U param3, I param4);
  }

  public RequestHandler4<T, R, U, I> handle(final Handler4<T, R, U, I> handler) {
    this.handler = handler;
    return this;
  }

  Response execute(final T param1, final R param2, final U param3, final I param4) {
    if (handler == null) throw new HandlerMissingException("No handle defined for " + method.toString() + " " + path);
    return handler.execute(param1, param2, param3, param4);
  }

  @Override
  Response execute(Request request, Action.MappedParameters mappedParameters) {
    final T param1 = resolverParam1.apply(request, mappedParameters);
    final R param2 = resolverParam2.apply(request, mappedParameters);
    final U param3 = resolverParam3.apply(request, mappedParameters);
    final I param4 = resolverParam4.apply(request, mappedParameters);
    return this.execute(param1, param2, param3, param4);
  }

  // region FluentAPI
  public <J> RequestHandler5<T, R, U, I, J> param(final Class<J> paramClass) {
    return new RequestHandler5<>(method, path, resolverParam1, resolverParam2, resolverParam3, resolverParam4, ParameterResolver.path(4, paramClass));
  }

  public <J> RequestHandler5<T, R, U, I, J> body(final Class<J> bodyClass) {
    return new RequestHandler5<>(method, path, resolverParam1, resolverParam2, resolverParam3, resolverParam4, ParameterResolver.body(bodyClass));
  }

  public RequestHandler5<T, R, U, I, String> query(final String name) {
    return query(name, String.class);
  }

  public <J> RequestHandler5<T, R, U, I, J> query(final String name, final Class<J> queryClass) {
    return new RequestHandler5<>(method, path, resolverParam1, resolverParam2, resolverParam3, resolverParam4, ParameterResolver.query(name, queryClass));
  }

  public RequestHandler5<T, R, U, I, Header> header(final String name) {
    return new RequestHandler5<>(method, path, resolverParam1, resolverParam2, resolverParam3, resolverParam4, ParameterResolver.header(name));
  }
  // endregion
}
