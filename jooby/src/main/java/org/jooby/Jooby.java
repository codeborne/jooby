package org.jooby;

import static java.util.Objects.requireNonNull;

import java.io.File;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import javax.annotation.Nonnull;

import org.jooby.Response.Status;
import org.jooby.Route.Err;
import org.jooby.internal.AssetRoute;
import org.jooby.internal.FallbackBodyConverter;
import org.jooby.internal.Server;
import org.jooby.internal.TypeConverters;
import org.jooby.internal.jetty.Jetty;
import org.jooby.internal.mvc.Routes;
import org.jooby.internal.routes.HeadFilter;
import org.jooby.internal.routes.OptionsRouter;
import org.jooby.internal.routes.TraceRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableList;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.google.inject.util.Types;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueFactory;

/**
 * <h1>Getting Started:</h1>
 * <p>
 * A new application must extends Jooby, register one ore more {@link BodyConverter} and defines
 * some {@link Route routes}. It sounds like a lot of work to do, but it isn't.
 * </p>
 *
 * <pre>
 * public class MyApp extends Jooby {
 *
 *   {
 *      use(new Jackson()); // 1. JSON body converter through Jackson.
 *
 *      // 2. Define a route
 *      get("/", (req, res) -> {
 *        Map<String, Object> model = ...;
 *        res.send(model);
 *      }
 *   }
 *
 *  public static void main(String[] args) throws Exception {
 *    new MyApp().start(); // 3. Done!
 *  }
 * }
 * </pre>
 *
 * <h1>Properties files</h1>
 * <p>
 * Jooby delegate configuration management to
 * <a href="https://github.com/typesafehub/config">TypeSafe Config</a>. If you are unfamiliar with
 * <a href="https://github.com/typesafehub/config">TypeSafe Config</a> please take a few minutes
 * to discover what <a href="https://github.com/typesafehub/config">TypeSafe Config</a> can do for
 * you.
 * </p>
 *
 * <p>
 * By default Jooby looks for an <code>application.conf</code> file at the root of the classpath.
 * If you want to specify a different file or location, you can do it with {@link #use(Config)}.
 * </p>
 *
 * <p>
 * <a href="https://github.com/typesafehub/config">TypeSafe Config</a> uses a hierarchical model to
 * define and override properties.
 * </p>
 * <p>
 * A {@link Jooby.Module} might provides his own set of properties through the
 * {@link Jooby.Module#config()} method. By default, this method returns an empty config object.
 * </p>
 * For example:
 * <pre>
 *   use(new M1());
 *   use(new M2());
 *   use(new M3());
 * </pre>
 * Previous example had the following order (first-listed are higher priority):
 * <ul>
 *  <li>System properties</li>
 *  <li>application.conf</li>
 *  <li>M3 properties</li>
 *  <li>M2 properties</li>
 *  <li>M1 properties</li>
 * </ul>
 * <p>
 * System properties takes precedence over any application specific property.
 * </p>
 *
 * <h1>Mode</h1>
 * <p>
 * Jooby defines two modes: <strong>dev</strong> or something else. In Jooby, <strong>dev</strong>
 * is special and some modules could apply special settings while running in <strong>dev</strong>.
 * Any other mode is usually considered a <code>prod</code> like mode. But that depends on module
 * implementor.
 * </p>
 * <p>
 * A mode can be defined in your <code>application.conf</code> file using the
 * <code>application.mode</code> property. If missing, Jooby set the mode for you to
 * <strong>dev</strong>.
 * </p>
 * <p>
 * There is more at {@link Mode} so take a few minutes to discover what a {@link Mode} can do for
 * you.
 * </p>
 *
 * <h1>Modules</h1>
 * <p>
 * {@link Jooby.Module Modules} are quite similar to a Guice modules except that the configure
 * callback has been complementing with {@link Mode} and {@link Config}.
 * </p>
 *
 * <pre>
 *   public class MyModule implements Jooby.Module {
 *     public void configure(Mode mode, Config config, Binder binder) {
 *     }
 *   }
 * </pre>
 *
 * From the configure callback you can bind your services as you usually do in a Guice app.
 * <p>
 * There is more at {@link Jooby.Module} so take a few minutes to discover what a
 * {@link Jooby.Module} can do for you.
 * </p>
 *
 * <h1>Path Patterns</h1>
 * <p>
 * Jooby supports Ant-style path patterns:
 * </p>
 * <p>
 * Some examples:
 * </p>
 * <ul>
 * <li>{@code com/t?st.html} - matches {@code com/test.html} but also {@code com/tast.jsp} or
 * {@code com/txst.html}</li>
 * <li>{@code com/*.html} - matches all {@code .html} files in the {@code com} directory</li>
 * <li><code>com/{@literal **}/test.html</code> - matches all {@code test.html} files underneath the
 * {@code com} path</li>
 * <li>{@code **}/{@code *} - matches any path at any level.</li>
 * <li>{@code *} - matches any path at any level, shorthand for {@code {@literal **}/{@literal *}.
 * </li>
 * </ul>
 *
 * <h2>Variables</h2>
 * <p>
 * Jooby supports path parameters too:
 * </p>
 * <p>
 * Some examples:
 * </p>
 * <ul>
 * <li><code> /user/{id}</code> - /user/* and give you access to the <code>id</code> var.</li>
 * <li><code> /user/:id</code> - /user/* and give you access to the <code>id</code> var.</li>
 * <li><code> /user/{id:\\d+}</code> - /user/[digits] and give you access to the numeric
 * <code>id</code> var.</li>
 * </ul>
 *
 * <h1>Routes</h1>
 * <p>
 * Routes perform actions in response to a server HTTP request. There are two types of routes
 * callback: {@link Router} and {@link Filter}.
 * </p>
 * <p>
 * Routes are executed in the order they are defined, for example:
 *
 * <pre>
 *   get("/", (req, res) -> {
 *     log.info("first"); // start here and go to second
 *   });
 *
 *   get("/", (req, res) -> {
 *     log.info("second"); // execute after first and go to final
 *   });
 *
 *   get("/", (req, res) -> {
 *     res.send("final"); // done!
 *   });
 * </pre>
 *
 * Please note first and second routes are converted to a filter, so previous example is the same
 * as:
 *
 * <pre>
 *   get("/", (req, res, chain) -> {
 *     log.info("first"); // start here and go to second
 *     chain.next(req, res);
 *   });
 *
 *   get("/", (req, res, chain) -> {
 *     log.info("second"); // execute after first and go to final
 *     chain.next(req, res);
 *   });
 *
 *   get("/", (req, res) -> {
 *     res.send("final"); // done!
 *   });
 * </pre>
 *
 * </p>
 *
 * <h2>Inline route</h2>
 * <p>
 * An inline route can be defined using Lambda expressions, like:
 * </p>
 *
 * <pre>
 *   get("/", (request, response) -> {
 *     response.send("Hello Jooby");
 *   });
 * </pre>
 *
 * Due to the use of lambdas a route is a singleton and you should NOT use global variables.
 * For example this is a bad practice:
 *
 * <pre>
 *  List<String> names = new ArrayList<>(); // names produces side effects
 *  get("/", (req, res) -> {
 *     names.add(req.param("name").stringValue();
 *     // response will be different between calls.
 *     res.send(names);
 *   });
 * </pre>
 *
 * <h2>External route</h2>
 * <p>
 * An external route can be defined by using a {@link Class route class}, like:
 * </p>
 *
 * <pre>
 *   get("/", route(ExternalRoute.class)); //or
 *
 *   ...
 *   // ExternalRoute.java
 *   public class ExternalRoute implements Router {
 *     public void handle(Request req, Response res) throws Exception {
 *       res.send("Hello Jooby");
 *     }
 *   }
 * </pre>
 *
 * <h2>Mvc Route</h2>
 * <p>
 * A Mvc Route use annotations to define routes:
 * </p>
 *
 * <pre>
 *   route(MyRoute.class);
 *   ...
 *   // MyRoute.java
 *   {@literal @}Path("/")
 *   public class MyRoute {
 *
 *    {@literal @}GET
 *    public String hello() {
 *      return "Hello Jooby";
 *    }
 *   }
 * </pre>
 * <p>
 * Programming model is quite similar to JAX-RS/Jersey with some minor differences and/or
 * simplifications.
 * </p>
 *
 * <p>
 * To learn more about Mvc Routes, please check {@link org.jooby.mvc.Path},
 * {@link org.jooby.mvc.Produces} {@link org.jooby.mvc.Consumes}, {@link org.jooby.mvc.Body} and
 * {@link org.jooby.mvc.Template}.
 * </p>
 *
 * <h1>Static Files</h1>
 * <p>
 * Static files, like: *.js, *.css, ..., etc... can be served with:
 * </p>
 *
 * <pre>
 *   assets("assets/**");
 * </pre>
 * <p>
 * Classpath resources under the <code>/assets</code> folder will be accessible from client/browser.
 * </p>
 * <h1>Bootstrap</h1>
 * <p>
 * The bootstrap process is defined as follows:
 * </p>
 * <h2>1. Configuration files are loaded in this order:</h2>
 * <ol>
 * <li>System properties</li>
 * <li>Application properties: {@code application.conf} or custom, see {@link #use(Config)}</li>
 * <li>Configuration properties from {@link Jooby.Module modules}</li>
 * </ol>
 *
 * <h2>2. Dependency Injection and {@link Jooby.Module modules}</h2>
 * <ol>
 * <li>An {@link Injector Guice Injector} is created.</li>
 * <li>It configures each registered {@link Jooby.Module module}</li>
 * <li>At this point Guice is ready and all the services has been binded.</li>
 * <li>The {@link JoobyModule#start() start method} is invoked.</li>
 * <li>Finally, Jooby starts the web server</li>
 * </ol>
 *
 * @author edgar
 * @since 0.1.0
 * @see Jooby.Module
 * @see Request
 * @see Response
 * @see BodyConverter
 * @see Router
 * @see Filter
 */
@Beta
public class Jooby {

  /**
   * A module can publish or produces: {@link Route.Definition routes},
   * {@link BodyConverter converters}, {@link Request.Module request modules} and any other
   * application specific service or contract of your choice.
   * <p>
   * It is similar to {@link com.google.inject.Module} except for the callback method receives a
   * {@link Mode}, {@link Config} and {@link Binder}.
   * </p>
   *
   * <p>
   * A module can provide his own set of properties through the {@link #config()} method. By
   * default, this method returns an empty config object.
   * </p>
   * For example:
   * <pre>
   *   use(new M1());
   *   use(new M2());
   *   use(new M3());
   * </pre>
   * Previous example had the following order (first-listed are higher priority):
   * <ul>
   *  <li>System properties</li>
   *  <li>application.conf</li>
   *  <li>M3 properties</li>
   *  <li>M2 properties</li>
   *  <li>M1 properties</li>
   * </ul>
   *
   * <p>
   * A module can provide start/stop methods in order to start or close resources.
   * </p>
   *
   * @author edgar
   * @since 0.1.0
   * @see Jooby#use(JoobyModule)
   */
  @Beta
  public static abstract class Module {

    /**
     * @return Produces a module config object (when need it). By default a module doesn't produce
     *         any configuration object.
     */
    public @Nonnull Config config() {
      return ConfigFactory.empty();
    }

    /**
     * Callback method to start a module. This method will be invoked after all the registered
     * modules
     * has been configured.
     *
     * @throws Exception If something goes wrong.
     */
    public void start() throws Exception {
    }

    /**
     * Callback method to stop a module and clean any resources. Invoked when the application is
     * about
     * to shutdown.
     *
     * @throws Exception If something goes wrong.
     */
    public void stop() throws Exception {
    }

    /**
     * Configure and produces bindings for the underlying application. A module can optimize or
     * customize a service by checking current the {@link Mode application mode} and/or the current
     * application properties available from {@link Config}.
     *
     * @param mode The current application's mode. Not null.
     * @param config The current config object. Not null.
     * @param binder A guice binder. Not null.
     * @throws Exception If the module fails during configuration.
     */
    public abstract void configure(@Nonnull Mode mode, @Nonnull Config config,
        @Nonnull Binder binder) throws Exception;
  }

  /**
   * Keep track of routes.
   */
  private final Set<Object> bag = new LinkedHashSet<>();

  /**
   * Keep track of modules.
   */
  private final Set<Jooby.Module> modules = new LinkedHashSet<>();

  /**
   * Keep track of singleton MVC routes.
   */
  private final Set<Class<?>> singletonRoutes = new LinkedHashSet<>();

  /**
   * Keep track of prototype MVC routes.
   */
  private final Set<Class<?>> protoRoutes = new LinkedHashSet<>();

  /**
   * The override config. Optional.
   */
  private Config config;

  /** The logging system. */
  protected final Logger log = LoggerFactory.getLogger(getClass());

  /** Keep the global injector instance. */
  private Injector injector;

  /** Error handler. */
  private Err.Handler err;

  /** Body converters. */
  private List<BodyConverter> converters = new LinkedList<>();

  /** Session store. */
  private Session.Definition session = new Session.Definition(Session.Store.NOOP);

  {
    use(new Jetty());
  }

  /**
   * Setup a session store to use. Useful if you want/need to persist sessions between shutdowns.
   * Sessions are not persisted by defaults.
   *
   * @param sessionStore A session store.
   * @return A session store definition.
   */
  public @Nonnull Session.Definition use(@Nonnull final Session.Store sessionStore) {
    this.session = new Session.Definition(requireNonNull(sessionStore,
        "A session store is required."));
    return this.session;
  }

  /**
   * Append a body converter to read/write HTTP messages.
   *
   * @param converter A body converter.
   * @return This jooby instance.
   */
  public @Nonnull Jooby use(@Nonnull final BodyConverter converter) {
    this.converters.add(requireNonNull(converter, "A body converter is required."));
    return this;
  }

  /**
   * Append a new filter that matches any method and path. This method is a shorthand for
   * {@link #use(String, Filter)}.
   *
   * @param filter A filter.
   * @return A new route definition.
   */
  public @Nonnull Route.Definition use(@Nonnull final Filter filter) {
    return use("*", filter);
  }

  /**
   * Append a new router that matches any method and path. This method is a shorthand for
   * {@link #use(String, Router)}.
   *
   * @param router A router.
   * @return A new route definition.
   */
  public @Nonnull Route.Definition use(@Nonnull final Router router) {
    return use("*", router);
  }

  /**
   * Append a new filter that matches any method under the given path.
   *
   * @param path A path pattern.
   * @param filter A filter.
   * @return A new route definition.
   */
  public @Nonnull Route.Definition use(final @Nonnull String path, final @Nonnull Filter filter) {
    return route(new Route.Definition("*", path, filter));
  }

  /**
   * Append a new router that matches any method under the given path.
   *
   * @param path A path pattern.
   * @param router A router.
   * @return A new route definition.
   */
  public @Nonnull Route.Definition use(final @Nonnull String path, final @Nonnull Router router) {
    return route(new Route.Definition("*", path, router));
  }

  /**
   * Define an in-line route that supports HTTP GET method:
   *
   * <pre>
   *   get("/", (req, res) -> {
   *     res.send(something);
   *   });
   * </pre>
   *
   * This is a singleton route so make sure you don't share or use global variables.
   *
   * @param path A path pattern.
   * @param route A route to execute.
   * @return A new route definition.
   */
  public @Nonnull Route.Definition get(final @Nonnull String path, final @Nonnull Router route) {
    return route(new Route.Definition("GET", path, route));
  }

  /**
   * Append a new in-line filter that supports HTTP GET method:
   *
   * <pre>
   *   get("/", (req, res, chain) -> {
   *     chain.next(req, res);
   *   });
   * </pre>
   *
   * This is a singleton route so make sure you don't share or use global variables.
   *
   * @param path A path pattern.
   * @param filter A route to execute.
   * @return A new route definition.
   */
  public @Nonnull Route.Definition get(final @Nonnull String path, final @Nonnull Filter filter) {
    return route(new Route.Definition("GET", path, filter));
  }

  /**
   * Append a new in-line route that supports HTTP POST method:
   *
   * <pre>
   *   post("/", (req, res) -> {
   *     res.send(something);
   *   });
   * </pre>
   *
   * This is a singleton route so make sure you don't share or use global variables.
   *
   * @param path A path pattern.
   * @param route A route to execute.
   * @return A new route definition.
   */
  public @Nonnull Route.Definition post(final @Nonnull String path, final @Nonnull Router route) {
    return route(new Route.Definition("POST", path, route));
  }

  /**
   * Append a new in-line route that supports HTTP POST method:
   *
   * <pre>
   *   post("/", (req, res, chain) -> {
   *     chain.next(req, res);
   *   });
   * </pre>
   *
   * This is a singleton route so make sure you don't share or use global variables.
   *
   * @param path A path pattern.
   * @param filter A filter to execute.
   * @return A new route definition.
   */
  public @Nonnull Route.Definition post(final @Nonnull String path, final @Nonnull Filter filter) {
    return route(new Route.Definition("POST", path, filter));
  }

  /**
   * Append a new in-line route that supports HTTP HEAD method:
   *
   * <pre>
   *   post("/", (req, res) -> {
   *     res.send(something);
   *   });
   * </pre>
   *
   * This is a singleton route so make sure you don't share or use global variables.
   *
   * @param path A path pattern.
   * @param route A route to execute.
   * @return A new route definition.
   */
  public Route.Definition head(final @Nonnull String path, final @Nonnull Router route) {
    return route(new Route.Definition("HEAD", path, route));
  }

  /**
   * Append a new in-line route that supports HTTP HEAD method:
   *
   * <pre>
   *   post("/", (req, res, chain) -> {
   *     chain.next(req, res);
   *   });
   * </pre>
   *
   * This is a singleton route so make sure you don't share or use global variables.
   *
   * @param path A path pattern.
   * @param filter A filter to execute.
   * @return A new route definition.
   */
  public @Nonnull Route.Definition head(final @Nonnull String path, final @Nonnull Filter filter) {
    return route(new Route.Definition("HEAD", path, filter));
  }

  /**
   * Append a new route that automatically handles HEAD request from existing GET routes.
   *
   * <pre>
   *   get("/", (req, res) -> {
   *     res.send(something); // This route provides default HEAD for this GET route.
   *   });
   * </pre>
   *
   * @param path A path pattern.
   * @return A new route definition.
   */
  public @Nonnull Route.Definition head(final @Nonnull String path) {
    return route(new Route.Definition("HEAD", path, filter(HeadFilter.class)).name("*.head"));
  }

  /**
   * Append a new in-line route that supports HTTP OPTIONS method:
   *
   * <pre>
   *   options("/", (req, res) -> {
   *     res.header("Allow", "GET, POST");
   *   });
   * </pre>
   *
   * This is a singleton route so make sure you don't share or use global variables.
   *
   * @param path A path pattern.
   * @param route A route to execute.
   * @return A new route definition.
   */
  public @Nonnull Route.Definition options(final @Nonnull String path,
      final @Nonnull Router route) {
    return route(new Route.Definition("OPTIONS", path, route));
  }

  /**
   * Append a new in-line route that supports HTTP OPTIONS method:
   *
   * <pre>
   *   options("/", (req, res, chain) -> {
   *     res.header("Allow", "GET, POST");
   *     chain.next(req, res);
   *   });
   * </pre>
   *
   * This is a singleton route so make sure you don't share or use global variables.
   *
   * @param path A path pattern.
   * @param filter A route to execute.
   * @return A new route definition.
   */
  public @Nonnull Route.Definition options(final @Nonnull String path,
      final @Nonnull Filter filter) {
    return route(new Route.Definition("OPTIONS", path, filter));
  }

  /**
   * Append a new route that automatically handles OPTIONS requests.
   *
   * <pre>
   *   get("/", (req, res) -> {
   *     res.send(something);
   *   });
   *
   *   post("/", (req, res) -> {
   *     res.send(something);
   *   });
   * </pre>
   * OPTINOS / produces a response with a Allow header set to: GET, POST.
   *
   * @param path A path pattern.
   * @return A new route definition.
   */
  public @Nonnull Route.Definition options(final @Nonnull String path) {
    return route(new Route.Definition("OPTIONS", path, router(OptionsRouter.class))
        .name("*.options"));
  }

  /**
   * Define an in-line route that supports HTTP PUT method:
   *
   * <pre>
   *   put("/", (req, res) -> {
   *     res.send(something);
   *   });
   * </pre>
   *
   * This is a singleton route so make sure you don't share or use global variables.
   *
   * @param path A path pattern.
   * @param route A route to execute.
   * @return A new route definition.
   */
  public @Nonnull Route.Definition put(final @Nonnull String path, final @Nonnull Router route) {
    return route(new Route.Definition("PUT", path, route));
  }

  /**
   * Define an in-line route that supports HTTP PUT method:
   *
   * <pre>
   *   put("/", (req, res, chain) -> {
   *     chain.next(req, res);
   *   });
   * </pre>
   *
   * This is a singleton route so make sure you don't share or use global variables.
   *
   * @param path A path pattern.
   * @param filer A route to execute.
   * @return A new route definition.
   */
  public @Nonnull Route.Definition put(final @Nonnull String path, final @Nonnull Filter filter) {
    return route(new Route.Definition("PUT", path, filter));
  }

  /**
   * Append a new in-line route that supports HTTP DELETE method:
   *
   * <pre>
   *   delete("/", (req, res) -> {
   *     res.status(304);
   *   });
   * </pre>
   *
   * This is a singleton route so make sure you don't share or use global variables.
   *
   * @param path A path pattern.
   * @param router A route to execute.
   * @return A new route definition.
   */
  public @Nonnull Route.Definition delete(final @Nonnull String path,
      final @Nonnull Router router) {
    return route(new Route.Definition("DELETE", path, router));
  }

  /**
   * Append a new in-line route that supports HTTP DELETE method:
   *
   * <pre>
   *   delete("/", (req, res, chain) -> {
   *     res.status(304);
   *     chain.next(req, res);
   *   });
   * </pre>
   *
   * This is a singleton route so make sure you don't share or use global variables.
   *
   * @param path A path pattern.
   * @param router A route to execute.
   * @return A new route definition.
   */
  public @Nonnull Route.Definition delete(final @Nonnull String path,
      final @Nonnull Filter filter) {
    return route(new Route.Definition("DELETE", path, filter));
  }

  /**
   * Append a new in-line route that supports HTTP TRACE method:
   *
   * <pre>
   *   trace("/", (req, res) -> {
   *     res.send(...);
   *   });
   * </pre>
   *
   * This is a singleton route so make sure you don't share or use global variables.
   *
   * @param path A path pattern.
   * @param router A route to execute.
   * @return A new route definition.
   */
  public @Nonnull Route.Definition trace(final @Nonnull String path, final @Nonnull Router route) {
    return route(new Route.Definition("TRACE", path, route));
  }

  /**
   * Append a new in-line route that supports HTTP TRACE method:
   *
   * <pre>
   *   trace("/", (req, res, chain) -> {
   *     chain.next(req, res);
   *   });
   * </pre>
   *
   * This is a singleton route so make sure you don't share or use global variables.
   *
   * @param path A path pattern.
   * @param filter A filter to execute.
   * @return A new route definition.
   */
  public @Nonnull Route.Definition trace(final @Nonnull String path,
      final @Nonnull Filter filter) {
    return route(new Route.Definition("TRACE", path, filter));
  }

  /**
   * Append a default trace implementation under the given path. Default trace response, looks
   * like:
   * <pre>
   *  TRACE /path
   *     header1: value
   *     header2: value
   *
   * </pre>
   *
   * @param path A path pattern.
   * @return
   */
  public @Nonnull Route.Definition trace(final @Nonnull String path) {
    return route(new Route.Definition("TRACE", path, router(TraceRouter.class))
        .name("*.trace"));
  }

  /**
   * Append a new in-line route that supports HTTP CONNECT method:
   *
   * <pre>
   *   connect("/", (req, res, chain) -> {
   *     chain.next(req, res);
   *   });
   * </pre>
   *
   * This is a singleton route so make sure you don't share or use global variables.
   *
   * @param path A path pattern.
   * @param router A router to execute.
   * @return A new route definition.
   */
  public @Nonnull Route.Definition connect(final @Nonnull String path,
      @Nonnull final Router router) {
    return route(new Route.Definition("CONNECT", path, router));
  }

  /**
   * Append a new in-line route that supports HTTP CONNECT method:
   *
   * <pre>
   *   connect("/", (req, res, chain) -> {
   *     chain.next(req, res);
   *   });
   * </pre>
   *
   * This is a singleton route so make sure you don't share or use global variables.
   *
   * @param path A path pattern.
   * @param filter A filter to execute.
   * @return A new route definition.
   */
  public @Nonnull Route.Definition connect(final @Nonnull String path,
      final @Nonnull Filter filter) {
    return route(new Route.Definition("CONNECT", path, filter));
  }

  /**
   * Creates a new {@link Router} that delegate the execution to the given router. This is useful
   * when the target router required some services and you want to instantiated with Guice.
   *
   * <pre>
   *   public class MyRouter implements Router {
   *     @Inject
   *     public MyRouter(Dependency d) {
   *     }
   *
   *     public void handle(Request req, Response res) throws Exception {
   *      // do something
   *     }
   *   }
   *   ...
   *   // external route
   *   get("/", router(MyRouter.class));
   *
   *   // inline version route
   *   get("/", (req, res) -> {
   *     Dependency d = req.getInstance(Dependency.class);
   *     // do something
   *   });
   * </pre>
   *
   * You can access to a dependency from a in-line route too, so the use of external route it is
   * more or less a matter of taste.
   *
   * @param router The external router class.
   * @return A new inline route.
   */
  public @Nonnull Router router(final @Nonnull Class<? extends Router> router) {
    requireNonNull(router, "A router type is required.");
    registerRouteScope(router);
    return (req, resp) -> req.getInstance(router).handle(req, resp);
  }

  /**
   * Creates a new {@link Filter} that delegate the execution to the given filter. This is useful
   * when the target filter required some services and you want to instantiated with Guice.
   *
   * <pre>
   *   public class MyFilter implements Filter {
   *     @Inject
   *     public MyFilter(Dependency d) {
   *     }
   *
   *     public void handle(Request req, Response res, Route.Chain chain) throws Exception {
   *      // do something
   *     }
   *   }
   *   ...
   *   // external filter
   *   get("/", router(MyFilter.class));
   *
   *   // inline version route
   *   get("/", (req, res, chain) -> {
   *     Dependency d = req.getInstance(Dependency.class);
   *     // do something
   *   });
   * </pre>
   *
   * You can access to a dependency from a in-line route too, so the use of external filter it is
   * more or less a matter of taste.
   *
   * @param router The external router class.
   * @return A new inline route.
   */
  public @Nonnull Filter filter(final @Nonnull Class<? extends Filter> filter) {
    requireNonNull(filter, "A filter type is required.");
    registerRouteScope(filter);
    return (req, res, chain) -> req.getInstance(filter).handle(req, res, chain);
  }

  /**
   * Serve or publish static files to browser.
   *
   * <pre>
   *   assets("/assets/**");
   * </pre>
   *
   * Resources are served from root of classpath, for example <code>GET /assets/file.js</code>
   * will be resolve as classpath resource at the same location.
   *
   * @param path The path to publish.
   * @return A new route definition.
   */
  public @Nonnull Route.Definition assets(final @Nonnull String path) {
    return get(path, router(AssetRoute.class)).name("static files");
  }

  /**
   * <p>
   * Append one or more routes defined in the given class.
   * </p>
   *
   * <pre>
   *   use(MyRoute.class);
   *   ...
   *   // MyRoute.java
   *   {@literal @}Path("/")
   *   public class MyRoute {
   *
   *    {@literal @}GET
   *    public String hello() {
   *      return "Hello Jooby";
   *    }
   *   }
   * </pre>
   * <p>
   * Programming model is quite similar to JAX-RS/Jersey with some minor differences and/or
   * simplifications.
   * </p>
   *
   * <p>
   * To learn more about Mvc Routes, please check {@link org.jooby.mvc.Path},
   * {@link org.jooby.mvc.Produces} {@link org.jooby.mvc.Consumes}, {@link org.jooby.mvc.Body} and
   * {@link org.jooby.mvc.Template}.
   * </p>
   *
   * @param routeResource The Mvc route.
   * @return This jooby instance.
   */
  public @Nonnull Jooby use(final @Nonnull Class<?> routeResource) {
    requireNonNull(routeResource, "Route resource is required.");
    registerRouteScope(routeResource);
    bag.add(routeResource);
    return this;
  }

  /**
   * Redirect to the given url with status code defaulting to {@link Status#FOUND}.
   *
   * <pre>
   *  res.redirect("/foo/bar");
   *  res.redirect("http://example.com");
   *  res.redirect("http://example.com");
   *  res.redirect("../login");
   * </pre>
   *
   * Redirects can be a fully qualified URI for redirecting to a different site:
   *
   * <pre>
   *   res.redirect("http://google.com");
   * </pre>
   *
   * Redirects can be relative to the root of the host name. For example, if you were
   * on <code>http://example.com/admin/post/new</code>, the following redirect to /admin would
   * land you at <code>http://example.com/admin</code>:
   *
   * <pre>
   *   res.redirect("/admin");
   * </pre>
   *
   * Redirects can be relative to the current URL. A redirection of post/new, from
   * <code>http://example.com/blog/admin/</code> (notice the trailing slash), would give you
   * <code>http://example.com/blog/admin/post/new.</code>
   *
   * <pre>
   *   res.redirect("post/new");
   * </pre>
   *
   * Redirecting to post/new from <code>http://example.com/blog/admin</code> (no trailing slash),
   * will take you to <code>http://example.com/blog/post/new</code>.
   *
   * <p>
   * If you found the above behavior confusing, think of path segments as directories (have trailing
   * slashes) and files, it will start to make sense.
   * </p>
   *
   * Pathname relative redirects are also possible. If you were on
   * <code>http://example.com/admin/post/new</code>, the following redirect would land you at
   * <code>http//example.com/admin</code>:
   *
   * <pre>
   *   res.redirect("..");
   * </pre>
   *
   * A back redirection will redirect the request back to the <code>Referer</code>, defaulting to
   * <code>/</code> when missing.
   *
   * <pre>
   *   res.redirect("back");
   * </pre>
   *
   * @param location Either a relative or absolute location.
   * @throws Exception If redirection fails.
   */
  public Router redirect(final String location) {
    return redirect(Status.FOUND, location);
  }

  /**
   * Redirect to the given url with status code defaulting to {@link Status#FOUND}.
   *
   * <pre>
   *  res.redirect("/foo/bar");
   *  res.redirect("http://example.com");
   *  res.redirect("http://example.com");
   *  res.redirect("../login");
   * </pre>
   *
   * Redirects can be a fully qualified URI for redirecting to a different site:
   *
   * <pre>
   *   res.redirect("http://google.com");
   * </pre>
   *
   * Redirects can be relative to the root of the host name. For example, if you were
   * on <code>http://example.com/admin/post/new</code>, the following redirect to /admin would
   * land you at <code>http://example.com/admin</code>:
   *
   * <pre>
   *   res.redirect("/admin");
   * </pre>
   *
   * Redirects can be relative to the current URL. A redirection of post/new, from
   * <code>http://example.com/blog/admin/</code> (notice the trailing slash), would give you
   * <code>http://example.com/blog/admin/post/new.</code>
   *
   * <pre>
   *   res.redirect("post/new");
   * </pre>
   *
   * Redirecting to post/new from <code>http://example.com/blog/admin</code> (no trailing slash),
   * will take you to <code>http://example.com/blog/post/new</code>.
   *
   * <p>
   * If you found the above behavior confusing, think of path segments as directories (have trailing
   * slashes) and files, it will start to make sense.
   * </p>
   *
   * Pathname relative redirects are also possible. If you were on
   * <code>http://example.com/admin/post/new</code>, the following redirect would land you at
   * <code>http//example.com/admin</code>:
   *
   * <pre>
   *   res.redirect("..");
   * </pre>
   *
   * A back redirection will redirect the request back to the <code>Referer</code>, defaulting to
   * <code>/</code> when missing.
   *
   * <pre>
   *   res.redirect("back");
   * </pre>
   *
   * @param status A redirect status.
   * @param location Either a relative or absolute location.
   * @throws Exception If redirection fails.
   */
  public Router redirect(final Response.Status status, final String location) {
    requireNonNull(location, "A location is required.");
    return (req, res) -> res.redirect(status, location);
  }

  /**
   * Check if the class had a Singleton annotation or not in order to register the route as singleton or prototype.
   * @param route
   */
  private void registerRouteScope(final Class<?> route) {
    if (route.getAnnotation(javax.inject.Singleton.class) == null) {
      protoRoutes.add(route);
    } else {
      singletonRoutes.add(route);
    }
  }

  /**
   * Keep track of routes in the order user define them.
   *
   * @param route A route definition to append.
   * @return The same route definition.
   */
  private Route.Definition route(final Route.Definition route) {
    bag.add(route);
    return route;
  }

  /**
   * Register a Jooby module.
   *
   * @param module The module to register.
   * @return This jooby instance.
   * @see Jooby.Module
   */
  public @Nonnull Jooby use(final @Nonnull Jooby.Module module) {
    requireNonNull(module, "A module is required.");
    modules.add(module);
    bag.add(module);
    return this;
  }

  /**
   * Set the application configuration object. You must call this method when the default file
   * name: <code>application.conf</code> doesn't work for you or when you need/want to register two
   * or more files.
   *
   * @param config The application configuration object.
   * @return This jooby instance.
   * @see Config
   */
  public @Nonnull Jooby use(final @Nonnull Config config) {
    this.config = requireNonNull(config, "A config is required.");
    return this;
  }

  /**
   * Setup a route error handler. Default error handler {@link Route.Err.Default} does content
   * negotation and this method allow to override/complement default handler.
   *
   * @param err A route error handler.
   * @return This jooby instance.
   */
  public @Nonnull Jooby err(final @Nonnull Route.Err.Handler err) {
    this.err = requireNonNull(err, "An err handler is required.");
    return this;
  }

  /**
   * Append a new WebSocket handler under the given path.
   *
   * <pre>
   *   ws("/ws", (socket) -> {
   *     // connected
   *     socket.onMessage(message -> {
   *       System.out.println(message);
   *     });
   *     socket.send("Connected"):
   *   });
   * </pre>
   *
   * @param path A path pattern.
   * @param A WebSocket handler.
   * @return A new WebSocket definition.
   */
  public @Nonnull WebSocket.Definition ws(final @Nonnull String path,
      final @Nonnull WebSocket.Handler handler) {
    WebSocket.Definition ws = new WebSocket.Definition(path, handler);
    bag.add(ws);
    return ws;
  }

  /**
   * <h1>Bootstrap</h1>
   * <p>
   * The bootstrap process is defined as follows:
   * </p>
   * <h2>1. Configuration files (first-listed are higher priority)</h2>
   * <ol>
   * <li>System properties</li>
   * <li>Application properties: {@code application.conf} or custom, see {@link #use(Config)}</li>
   * <li>{@link Jooby.Module Modules} properties</li>
   * </ol>
   *
   * <h2>2. Dependency Injection and {@link Jooby.Module modules}</h2>
   * <ol>
   * <li>An {@link Injector Guice Injector} is created.</li>
   * <li>It calls to {@link Jooby.Module#configure(Mode, Config, Binder)} for each module.</li>
   * <li>At this point Guice is ready and all the services has been binded.</li>
   * <li>It calls to {@link Jooby.Module#start() start method} for each module.</li>
   * <li>A web server is started</li>
   * </ol>
   *
   * @throws Exception If something fails to start.
   */
  public void start() throws Exception {
    config = buildConfig(Optional.ofNullable(config));

    Mode mode = mode(config.getString("application.mode").toLowerCase());

    // shutdown hook
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      try {
        stop();
      } catch (Exception ex) {
        log.error("Shutdown with error", ex);
      }
    }));

    final Charset charset = Charset.forName(config.getString("application.charset"));

    String[] lang = config.getString("application.lang").split("_");
    final Locale locale = lang.length == 1 ? new Locale(lang[0]) : new Locale(lang[0], lang[1]);

    DateTimeFormatter dateTimeFormat = DateTimeFormatter
        .ofPattern(config.getString("application.dateFormat"), locale);

    DecimalFormat numberFormat = new DecimalFormat(config.getString("application.numberFormat"));

    // dependency injection
    injector = Guice.createInjector(new com.google.inject.Module() {
      @Override
      public void configure(final Binder binder) {

        TypeConverters.configure(binder);

        // bind config
        bindConfig(binder, config);

        // bind mode
        binder.bind(Mode.class).toInstance(mode);

        // bind charset
        binder.bind(Charset.class).toInstance(charset);

        // bind locale
        binder.bind(Locale.class).toInstance(locale);

        // bind date format
        binder.bind(DateTimeFormatter.class).toInstance(dateTimeFormat);

        // bind number format
        binder.bind(NumberFormat.class).toInstance(numberFormat);
        binder.bind(DecimalFormat.class).toInstance(numberFormat);

        // bind readers & writers
        Multibinder<BodyConverter> converterBinder = Multibinder
            .newSetBinder(binder, BodyConverter.class);

        // session definition
        binder.bind(Session.Definition.class).toInstance(session);

        // Routes
        Multibinder<Route.Definition> definitions = Multibinder
            .newSetBinder(binder, Route.Definition.class);

        // Web Sockets
        Multibinder<WebSocket.Definition> sockets = Multibinder
            .newSetBinder(binder, WebSocket.Definition.class);

        // Request Modules
        Multibinder<Request.Module> requestModule = Multibinder
            .newSetBinder(binder, Request.Module.class);

        // bind prototype routes in request module
        requestModule.addBinding().toInstance(
            rm -> protoRoutes.forEach(routeClass -> rm.bind(routeClass)));

        // tmp dir
        binder.bind(File.class).annotatedWith(Names.named("java.io.tmpdir"))
            .toInstance(new File(config.getString("java.io.tmpdir")));

        // converters
        converters.forEach(it -> converterBinder.addBinding().toInstance(it));

        // modules, routes and websockets
        bag.forEach(candidate -> {
          if (candidate instanceof Jooby.Module) {
            install((Jooby.Module) candidate, mode, config, binder);
          } else if (candidate instanceof Route.Definition) {
            definitions.addBinding().toInstance((Route.Definition) candidate);
          } else if (candidate instanceof WebSocket.Definition) {
            sockets.addBinding().toInstance((WebSocket.Definition) candidate);
          } else {
            Class<?> routeClass = (Class<?>) candidate;
            Routes.routes(mode, routeClass)
                .forEach(route -> definitions.addBinding().toInstance(route));
          }
        });

        // Singleton routes
        singletonRoutes.forEach(routeClass -> binder.bind(routeClass).in(Scopes.SINGLETON));

        converterBinder.addBinding().toInstance(FallbackBodyConverter.COPY_TEXT);
        converterBinder.addBinding().toInstance(FallbackBodyConverter.COPY_BYTES);
        converterBinder.addBinding().toInstance(FallbackBodyConverter.READ_TEXT);
        converterBinder.addBinding().toInstance(FallbackBodyConverter.TO_HTML);

        // err
        if (err == null) {
          binder.bind(Err.Handler.class).toInstance(new Err.Default());
        } else {
          binder.bind(Err.Handler.class).toInstance(err);
        }
      }

    });

    // start modules
    for (Jooby.Module module : modules) {
      module.start();
    }

    // Start server
    Server server = injector.getInstance(Server.class);

    server.start();
  }

  /**
   * Stop the application, close all the modules and stop the web server.
   */
  public void stop() throws Exception {
    // stop modules
    for (Jooby.Module module : modules) {
      try {
        module.stop();
      } catch (Exception ex) {
        log.error("Can't stop: " + module.getClass().getName(), ex);
      }
    }

    try {
      if (injector != null) {
        Server server = injector.getInstance(Server.class);
        server.stop();
      }
    } catch (Exception ex) {
      log.error("Can't stop server", ex);
    }
  }

  /**
   * Build configuration properties, it configure system, app and modules properties.
   *
   * @param appConfig An optional app configuration.
   * @return A configuration properties ready to use.
   */
  private Config buildConfig(final Optional<Config> appConfig) {
    Config sysProps = ConfigFactory.defaultOverrides()
        // file encoding got corrupted sometimes so we force and override.
        .withValue("file.encoding",
            ConfigValueFactory.fromAnyRef(System.getProperty("file.encoding")));

    // app configuration
    Supplier<Config> defaults = () -> ConfigFactory.parseResources("application.conf");
    Config config = sysProps
        .withFallback(appConfig.orElseGet(defaults));

    // set app name
    if (!config.hasPath("application.name")) {
      config = config.withValue("application.name",
          ConfigValueFactory.fromAnyRef(getClass().getSimpleName()));
    }

    // set default charset, if app config didn't set it
    if (!config.hasPath("application.charset")) {
      config = config.withValue("application.charset",
          ConfigValueFactory.fromAnyRef(Charset.defaultCharset().name()));
    }

    // locale
    final Locale locale;
    if (!config.hasPath("application.lang")) {
      locale = Locale.getDefault();
      config = config.withValue("application.lang",
          ConfigValueFactory.fromAnyRef(locale.getLanguage() + "_" + locale.getCountry()));
    } else {
      String[] lang = config.getString("application.lang").split("_");
      locale = lang.length == 1 ? new Locale(lang[0]) : new Locale(lang[0], lang[1]);
    }

    // date format
    if (!config.hasPath("application.dateFormat")) {
      String pattern = new SimpleDateFormat(new SimpleDateFormat().toPattern(), locale).toPattern();
      config = config.withValue("application.dateFormat", ConfigValueFactory.fromAnyRef(pattern));
    }

    // number format
    if (!config.hasPath("application.numberFormat")) {
      String pattern = ((DecimalFormat) DecimalFormat.getInstance(locale)).toPattern();
      config = config.withValue("application.numberFormat", ConfigValueFactory.fromAnyRef(pattern));
    }

    // set module config
    for (Jooby.Module module : ImmutableList.copyOf(modules).reverse()) {
      config = config.withFallback(module.config());
    }

    // add default config + mime types
    config = config
        .withFallback(ConfigFactory.parseResources("org/jooby/mime.properties"));
    config = config
        .withFallback(ConfigFactory.parseResources("org/jooby/jooby.conf"));

    // last check app secret
    if (!config.hasPath("application.secret")) {
      String mode = config.getString("application.mode");
      if ("dev".equalsIgnoreCase(mode)) {
        // it will survive between restarts and allow to have different apps running for
        // development.
        String devRandomSecret = getClass().getResource(".").toString();
        config = config.withValue("application.secret",
            ConfigValueFactory.fromAnyRef(devRandomSecret));
      } else {
        throw new IllegalStateException("No application.secret has been defined");
      }
    }

    return config.resolve();
  }

  /**
   * Install a {@link JoobyModule}.
   *
   * @param module The module to install.
   * @param mode Application mode.
   * @param config The configuration object.
   * @param binder A Guice binder.
   */
  private void install(final Jooby.Module module, final Mode mode, final Config config,
      final Binder binder) {
    try {
      module.configure(mode, config, binder);
    } catch (Exception ex) {
      throw new IllegalStateException("Module didn't start properly: "
          + module.getClass().getName(), ex);
    }
  }

  /**
   * Bind a {@link Config} and make it available for injection. Each property of the config is also
   * binded it and ready to be injected with {@link javax.inject.Named}.
   *
   * @param binder
   * @param config
   */
  @SuppressWarnings("unchecked")
  private void bindConfig(final Binder root, final Config config) {
    Binder binder = root.skipSources(Names.class);
    for (Entry<String, ConfigValue> entry : config.entrySet()) {
      String name = entry.getKey();
      Named named = Names.named(name);
      Object value = entry.getValue().unwrapped();
      if (value instanceof List) {
        List<Object> values = (List<Object>) value;
        Type listType = Types.listOf(values.iterator().next().getClass());
        Key<Object> key = (Key<Object>) Key.get(listType, Names.named(name));
        binder.bind(key).toInstance(values);
      } else {
        @SuppressWarnings("rawtypes")
        Class type = value.getClass();
        binder.bind(type).annotatedWith(named).toInstance(value);
      }
    }
    // bind config
    binder.bind(Config.class).toInstance(config);
  }

  /**
   * Creates the application's mode.
   *
   * @param name A mode's name.
   * @return A new mode.
   */
  private static Mode mode(final String name) {
    return new Mode() {
      @Override
      public String name() {
        return name;
      }

      @Override
      public String toString() {
        return name;
      }
    };
  }
}
