package com.github.daggerok

import java.util.UUID
import java.util.concurrent.CompletionStage

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.stream.ActorMaterializer
import akka.util.Timeout
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.{ApplicationRunner, SpringApplication}
import org.springframework.context.annotation.{Bean, Configuration}
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import org.springframework.stereotype.{Repository, Service}
import org.springframework.web.reactive.function.server
import org.springframework.web.reactive.function.server._
import reactor.core.publisher.{Flux, Mono}

import scala.beans.BeanProperty
import scala.jdk.javaapi.{CollectionConverters, FutureConverters}
import scala.language.postfixOps
import scala.util.{Failure, Success}

@Document
case class Author(@BeanProperty @Id var name: String)

@Document
case class HashTag(@BeanProperty @Id var name: String)

@Document
case class Tweet(@BeanProperty var body: String,
                 @BeanProperty var author: Author,
                 @BeanProperty @Id var id: UUID = UUID.randomUUID()) {
  @BeanProperty
  var hashTags: java.util.Set[HashTag] =
    CollectionConverters.asJava(
      body.split(" ")
        .collect { case text if text startsWith "#" => HashTag(text.replace("^#\\w+", "") toLowerCase) }
        .toSet)
}

@Repository
trait Tweets extends ReactiveMongoRepository[Tweet, UUID]

@Configuration
class AkkaConfig {
  @Bean def actorSystem: ActorSystem = ActorSystem.create("TweetsSystem")

  @Bean def actorMaterializer: ActorMaterializer = ActorMaterializer.create(actorSystem)
}

object TweetsActor {
  sealed trait Messages
  case class SaveTweet(tweet: Tweet) extends Messages
  case class FindTweetBy(tweet: Tweet) extends Messages
  case object FindAllTweets extends Messages
}

class TweetsActor(tweets: Tweets) extends Actor with ActorLogging {
  import TweetsActor._
  implicit val executor = context.dispatcher

  override def preStart(): Unit = log.info("{} starting...", self.path)

  override def receive: Receive = {
    case SaveTweet(tweet) =>
      log.info("savig tweet: {}", tweet)
      val listener = sender
      val javaFuture = tweets.save(tweet).toFuture
      val scalaFuture = FutureConverters.asScala(javaFuture)
      scalaFuture onComplete {
        case Success(savedTweet) =>
          listener ! savedTweet
          context stop self
        case Failure(exception) =>
          log.error("fuck, no! {}", exception.getLocalizedMessage)
          context stop self
      }
  }

  override def postStop(): Unit = log.info("{} stopped.", self.path)
}

@Service
class TweetsHandlers(val tweets: Tweets,
                     val actorSystem: ActorSystem) {

  private val log = LoggerFactory.getLogger(classOf[TweetsHandlers])

  def getInfo(request: ServerRequest): Mono[ServerResponse] = {
    log.info("{}", request)
    val uri = request.uri()
    val baseUrl = s"${uri.getScheme}://${uri.getAuthority}"
    val map = CollectionConverters.asJava(Map(
      "post new" -> s"POST $baseUrl/tweets author={author} body={body}",
      "get tags" -> s"GET $baseUrl/tweets/{id}/tags",
      "get tweet" -> s"GET $baseUrl/tweets/{id}",
      "get tweets" -> s"GET $baseUrl/tweets"
    ))
    ServerResponse.ok().body(Mono.just(map), classOf[java.util.Map[String, String]])
  }

  def getTweet(request: ServerRequest): Mono[ServerResponse] = {
    log.info("{}", request)
    val id: String = request.pathVariable("id")
    val tweet = tweets.findById(UUID.fromString(id))
    ServerResponse.ok().body(tweet, classOf[Tweet])
  }

  def getTweets(request: server.ServerRequest): Mono[ServerResponse] =
    ServerResponse.ok().body(
      tweets.findAll().doFinally(_ => log.info("{}", request)), classOf[Tweet])

  // NOTE: here we will be using akka just for fun, to show
  // how pipeline data flow between spring webflux and akka...
  def postTweet(request: server.ServerRequest): Mono[ServerResponse] = {
    log.info("{}", request)
    val input: Mono[java.util.Map[String, String]] =
      request.bodyToMono(classOf[java.util.Map[String, String]])
    val tweetToBeSaved: Mono[Tweet] = input
      .map[(String, String)](m => {
        val body = m.getOrDefault("body", "")
        val author = m.getOrDefault("author", "anonymous")
        (body, author)
      })
      .map(m => Tweet(m._1, Author(m._2)))
    val tweet: Mono[Any] = tweetToBeSaved.flatMap(
      tweet => Mono.fromCompletionStage(process(tweet)))
    ServerResponse.ok().body(tweet, classOf[Any])
  }

  private def process(tweet: Tweet): CompletionStage[Any] = {
    val props = Props(new TweetsActor(tweets))
    val tweetActorRef = actorSystem actorOf(props, s"tweetsActor-${System.nanoTime()}")
    import TweetsActor._
    import akka.pattern.ask
    import scala.concurrent.duration._
    implicit val timeout = Timeout(5.seconds)
    implicit val executor = actorSystem.dispatcher
    FutureConverters.asJava(tweetActorRef ? SaveTweet(tweet))
  }

  def getTweetTags(request: server.ServerRequest): Mono[ServerResponse] = {
    log.info("{}", request)
    val id = request.pathVariable("id")
    val tags: Mono[java.util.Set[HashTag]] = tweets
      .findById(UUID.fromString(id))
      .map(t => t.hashTags)
    ServerResponse.ok().body(tags, classOf[java.util.Set[HashTag]])
  }

  def getTags(request: server.ServerRequest): Mono[ServerResponse] = {
    log.info("{}", request)
    val tags: Flux[HashTag] = tweets
      .findAll()
      .flatMap(t => Flux.fromIterable(t.hashTags))
    ServerResponse.ok().body(tags, classOf[HashTag])
  }
}

@Configuration
class RouterFunctionBuilderConfig(val handlers: TweetsHandlers) {
  @Bean
  def routers: RouterFunction[ServerResponse] = RouterFunctions.route()
    .GET("/tweets/tags", handlers.getTags _)
    .GET("/tweets/{id}", handlers.getTweet _)
    .GET("/tweets/{id}/tags", handlers.getTweetTags _)
    .POST("/tweets/**", handlers.postTweet _)
    .GET("/tweets/**", handlers.getTweets _)
    .build()
    .andRoute(RequestPredicates.path("/**"), handlers.getInfo _)
}

@SpringBootApplication
class SpringBootReactiveAkkaApplication(tweets: Tweets) {
  private val log = LoggerFactory.getLogger(classOf[SpringBootReactiveAkkaApplication])

  @Bean def testData: ApplicationRunner = _ => {
    val max = Author("max")
    val dag = Author("dag")
    val daggerok = Author("daggerok")
    val stream = Flux.just(
      Tweet("#spring-boot is nice!", max),
      Tweet("and #scala too!", dag),
      Tweet("But both together are freaking awesome!!111oneoneone #scala with #spring-boot", daggerok)
    )
    tweets
      .deleteAll()
      .thenMany(tweets.saveAll(stream))
      .thenMany(tweets.findAll())
      .subscribe(t => log.info(
        s"""
           |
           |@${t.author.name}:
           |${t.body}
           |hash-tag${if (t.hashTags.size == 1) "" else "s"}: ${CollectionConverters.asScala(t.hashTags).map(_.name).mkString(" ")}
         """.stripMargin)
      )
  }
}

object SpringBootReactiveAkkaApplication extends App {
  SpringApplication.run(classOf[SpringBootReactiveAkkaApplication], args: _*)
}
