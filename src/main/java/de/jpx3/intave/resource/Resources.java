package de.jpx3.intave.resource;

import de.jpx3.intave.IntavePlugin;
import de.jpx3.intave.connect.IntaveDomains;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;

import static java.nio.charset.StandardCharsets.UTF_8;

public final class Resources {
  public static Resource resourceFromFile(File file) {
    return new FileResource(file);
  }

  public static Resource resourceFromJar(String path) {
    return new ClasspathResource(path);
  }

  public static Resource resourceFromJarWithFallback(String path, Resource fallback) {
    return new ClasspathResource(path, fallback);
  }

  public static Resource resourceFromJarOrBuild(String path) {
    return resourceFromJarWithFallback(path, resourceFromFile(new File("src/main/java/resources/" + path)));
  }

  public static Resource resourceFromFileWithLock(File file) {
    return resourceFromFile(file).locked(file);
  }

  public static Resource resourceFromFileWithHashAndLock(File file) {
    return resourceFromFile(file).locked(file);
  }

  public static Resource resourceFromWeb(URL url) {
    return new WebResource(url);
  }

  public static Resource resourceFromWebWithFallback(URL url, Resource fallback) {
    return new WebResource(url, fallback);
  }

  public static Resource memoryResource() {
    return new MemoryResource();
  }

  public static Resource resourceFromOneOf(URL[] urls) {
    Resource previous = null;
    for (int i = urls.length - 1; i >= 0; i--) {
      previous = resourceFromWebWithFallback(urls[i], previous);
    }
    return previous;
  }

  static Resource withLockingFile(File targetFile, Resource resource) {
    return new LockingLayer(targetFile, resource);
  }

  static Resource refreshFileAccessDateOnRead(File targetFile, Resource resource) {
    return new FileAccessTimeRefreshLayer(resource, targetFile);
  }

  static Resource withCompression(Resource resource) {
    // add later
    return new CompressionLayer(resource);
  }

  static Resource retryRead(Resource resource, int retries) {
    return new RetryReadLayer(resource, retries);
  }

  static Resource withFileSpread(File file, Function<File, Resource> resourcer, int spreads) {
    return new FileSpreadLayer(file, resourcer, spreads);
  }

  public static Resource fileCache(
    String identifier
  ) {
    try {
      String name = nameFrom(new URL("https://google.com"), identifier, Long.MAX_VALUE);
      return withFileSpread(cacheFileLocationOf(name), Resources::resourceFromFileWithLock, 8);
    } catch (MalformedURLException exception) {
      throw new IllegalStateException(exception);
    }
  }

  public static Resource cacheResourceChain(
    String urlString,
    String identifier,
    long expires
  ) {
    URL url;
    try {
      url = new URL(urlString);
    } catch (MalformedURLException exception) {
      throw new IllegalStateException(exception);
    }
    File initialFile = cacheFileLocationOf(nameFrom(url, identifier, expires));
    Resource cache = withFileSpread(initialFile, Resources::resourceFromFileWithLock, 8);
    Resource access = resourceFromWeb(url);
    Resource resourceCache = new ResourceCache(cache, access, expires).retryReads(2);
    ResourceRegistry.registerResource(identifier, resourceCache);
    return resourceCache;
  }

  public static Resource localServiceCacheResource(
    String localPath,
    String identifier,
    long expires
  ) {
    return cacheResourceChainWithMultipleDomains(
      "https://{DOMAIN}/" + localPath,
      IntaveDomains.serviceDomains(),
      identifier, expires
    );
  }

  private static Resource cacheResourceChainWithMultipleDomains(
    String pattern,
    List<String> domains,
    String identifier,
    long expires
  ) {
    URL[] urls = new URL[domains.size()];
    for (int i = 0; i < urls.length; i++) {
      try {
        urls[i] = new URL(pattern.replace("{DOMAIN}", domains.get(i)));
      } catch (MalformedURLException exception) {
        throw new IllegalStateException(exception);
      }
    }
    File initialFile = cacheFileLocationOf(nameFrom(urls[0], identifier, expires));
    Resource cache = withFileSpread(initialFile, Resources::resourceFromFileWithLock, 8);
    Resource access = resourceFromOneOf(urls);
    Resource resourceCache = new ResourceCache(cache, access, expires).retryReads(2);
    ResourceRegistry.registerResource(identifier, resourceCache);
    return resourceCache;
  }

  private static String nameFrom(URL url, String identifier, long expires) {
    UUID uuid = UUID.nameUUIDFromBytes((IntavePlugin.version() + ":" + identifier + ":" + url + ":" + expires).getBytes(UTF_8));
    return uuid.toString().replace("-", "")
      .replace("f", "r")
      .replace("e", "y")
      .replace("c", "i");
  }

  static InputStream subscribeToClose(InputStream initial, Runnable onClose) {
    return new FilterInputStream(initial) {
      boolean closed = false;

      @Override
      public void close() throws IOException {
        super.close();
        onClose.run();
        closed = true;
      }

      @Override
      protected void finalize() {
        if (!closed) {
          throw new IllegalStateException("InputStream was not closed");
        }
      }
    };
  }

  static OutputStream subscribeToClose(OutputStream initial, Runnable onClose) {
    return new FilterOutputStream(initial) {
      boolean closed = false;

      @Override
      public synchronized void close() throws IOException {
        super.close();
        if (closed) {
          return;
        }
        onClose.run();
        closed = true;
      }

      @Override
      protected void finalize() {
        if (!closed) {
          throw new IllegalStateException("OutputStream was not closed");
        }
      }
    };
  }

  private static File cacheFileLocationOf(String resourceId) {
    String operatingSystem = System.getProperty("os.name").toLowerCase(Locale.ROOT);
    String filePath;
    if (operatingSystem.contains("win")) {
      filePath = System.getenv("APPDATA") + "/Intave/Cache/";
    } else {
      filePath = System.getProperty("user.home") + "/.intave/cache/";
    }
    File workDirectory = new File(filePath + "/" + (resourceId.length() > 4 ? resourceId.substring(0, 4) : "????") + "/");
    if (!workDirectory.exists()) {
      workDirectory.mkdirs();
    }
    return new File(workDirectory, resourceId.length() > 4 ? resourceId.substring(4) : resourceId);
  }
}
