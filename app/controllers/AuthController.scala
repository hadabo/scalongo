package controllers

import java.util.UUID

import actions.SecureAction
import com.github.t3hnar.bcrypt._
import com.google.inject.Inject
import com.mongodb.MongoWriteException
import forms.AuthForms.{LoginData, SignupData}
import models.{Session, User}
import play.api.i18n.MessagesApi
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.{JsValue, Json}
import play.api.mvc._
import services.{SessionService, UserService}

import scala.concurrent.Future

/**
  * Created by ismet on 13/12/15.
  */
class AuthController @Inject()(userService: UserService,
                               sessionService: SessionService,
                               secureAction: SecureAction,
                               val messagesApi: MessagesApi) extends Controller {

  def signup = Action.async { implicit request =>
    val rawBody: JsValue = request.body.asJson.get
    val signupData: SignupData = rawBody.validate[SignupData].get

    val userObj = Json.obj(
      "_id" -> UUID.randomUUID().toString,
      "name" -> signupData.name,
      "email" -> signupData.email,
      "username" -> signupData.username,
      "password" -> signupData.password.bcrypt,
      "timestamp" -> System.currentTimeMillis())

    val user = User(userObj)

    userService.save(user).map((_) => {
      Ok
    })
  }

  def login = Action.async { implicit request =>
    val rawBody: JsValue = request.body.asJson.get
    val loginData = rawBody.validate[LoginData].get

    userService.findByUsername(loginData.username).map((user: User) => {
      if (loginData.password.isBcrypted(user.password)) {
        val sessionId: String = UUID.randomUUID().toString
        val currentTimeMillis: Long = System.currentTimeMillis()
        val session: Session = models.Session(Json.obj(
          "_id" -> sessionId,
          "userId" -> user._id,
          "ip" -> request.remoteAddress,
          "userAgent" -> request.headers.get("User-Agent").get,
          "timestamp" -> currentTimeMillis,
          "timeUpdate" -> currentTimeMillis
        ))
        sessionService.save(session)
        val response = Map("sessionId" -> sessionId)
        Ok(Json.toJson(response)).withCookies(Cookie("sessionId", sessionId))
      } else {
        Unauthorized
      }
    })
  }

  def securedSampleAction = secureAction { implicit request =>
    Ok
  }

  def logout = secureAction { implicit request =>
    sessionService.delete(request.sessionId)
    Ok.discardingCookies(DiscardingCookie("sessionId"))
  }


}
