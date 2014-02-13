/**
 * Copyright (C) 2014 Spotify AB
 */

package com.spotify.helios.common;

import com.google.common.base.Supplier;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.SRVRecord;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import static java.lang.String.format;

public abstract class Resolver {

  private static final Logger log = LoggerFactory.getLogger(Resolver.class);

  public static Supplier<List<URI>> supplier(final String srvName, final String domain) {
    return new Supplier<List<URI>>() {
      @Override
      public List<URI> get() {
        return resolve(srvName, domain);
      }
    };
  }

  public static List<URI> resolve(final String srvName, final String domain) {
    final String fqdn = endpoint(srvName, domain);
    final Lookup lookup;
    try {
      lookup = new Lookup(fqdn, Type.SRV, DClass.IN);
    } catch (TextParseException e) {
      throw new IllegalArgumentException("unable to create lookup for name: " + fqdn, e);
    }

    Record[] queryResult = lookup.run();

    switch (lookup.getResult()) {
      case Lookup.SUCCESSFUL:
        final ImmutableList.Builder<URI> endpoints = ImmutableList.builder();
        for (Record record : queryResult) {
          if (record instanceof SRVRecord) {
            SRVRecord srv = (SRVRecord) record;
            endpoints.add(http(srv.getTarget().toString(), srv.getPort()));
          }
        }
        return endpoints.build();
      case Lookup.HOST_NOT_FOUND:
        // fallthrough
      case Lookup.TYPE_NOT_FOUND:
        log.warn("No results returned for query '{}'", fqdn);
        return ImmutableList.of();
      default:
        throw new HeliosRuntimeException(String.format("Lookup of '%s' failed with code: %d - %s ",
                                                       fqdn, lookup.getResult(),
                                                       lookup.getErrorString()));
    }
  }

  private static URI http(final String host, final int port) {
    final URI endpoint;
    try {
      endpoint = new URI("http", null, host, port, null, null, null);
    } catch (URISyntaxException e) {
      throw Throwables.propagate(e);
    }
    return endpoint;
  }

  private static String endpoint(final String name, final String site) {
    final String domain;
    if (site.contains("spotify.net") || site.endsWith(".")) {
      domain = site;
    } else {
      domain = site + ".spotify.net.";
    }
    return format("_spotify-%s._http.services.%s", name, domain);
  }

}