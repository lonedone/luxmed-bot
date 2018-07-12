/**
  * MIT License
  *
  * Copyright (c) 2018 Yevhen Zadyra
  *
  * Permission is hereby granted, free of charge, to any person obtaining a copy
  * of this software and associated documentation files (the "Software"), to deal
  * in the Software without restriction, including without limitation the rights
  * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
  * copies of the Software, and to permit persons to whom the Software is
  * furnished to do so, subject to the following conditions:
  *
  * The above copyright notice and this permission notice shall be included in all
  * copies or substantial portions of the Software.
  *
  * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
  * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
  * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
  * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
  * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
  * SOFTWARE.
  */
package com.lbs.server.actor

import akka.actor.{ActorRef, Props}
import com.lbs.bot.model.{Button, Command}
import com.lbs.bot.{Bot, _}
import com.lbs.server.actor.Account._
import com.lbs.server.actor.Login._
import com.lbs.server.actor.conversation.Conversation
import com.lbs.server.lang.{Localizable, Localization}
import com.lbs.server.service.DataService
import com.lbs.server.util.MessageExtractors.CallbackCommand

class Account(val userId: UserId, bot: Bot, dataService: DataService, val localization: Localization, router: ActorRef) extends Conversation[Unit] with Localizable {

  entryPoint(askAction)

  def askAction: Step =
    ask { _ =>
      val credentials = dataService.getUserCredentials(userId.userId)
      val currentAccount = credentials.find(c => c.accountId == userId.accountId).getOrElse(sys.error("Can't determine current account"))
      val buttons = Seq(Button(lang.addAccount, -1L), Button(lang.deleteAccount, -2L)) ++ credentials.map(c => Button(s"🔐️ ${c.username}", c.accountId))
      bot.sendMessage(userId.source, lang.pleaseChooseAccount(currentAccount.username), inlineKeyboard = createInlineKeyboard(buttons, columns = 1))
    } onReply {
      case Msg(cmd@CallbackCommand(actionStr), _) =>
        val action = actionStr.toLong
        action match {
          case -1L =>
            router ! cmd.copy(message = cmd.message.copy(text = Some("/login")))
            stay()
          case -2L =>
            bot.sendMessage(userId.source, "Not implemented yet")
            stay()
          case accountId =>
            val accountMaybe = dataService.findUserCredentialsByAccountId(userId.userId, accountId)
            accountMaybe match {
              case Some(account) => //account was found
                val userMaybe = dataService.findUser(userId.userId)
                userMaybe.foreach { user =>
                  user.activeAccountId = accountId
                  dataService.saveUser(user)
                  router ! SwitchUser(UserId(account.userId, account.accountId, userId.source))
                  bot.sendMessage(userId.source, lang.accountSwitched(account.username))
                }
                stay()
              case None =>
                error(s"This is not user [#${userId.userId}] account [#$accountId]")
                stay()
            }
        }
    }
}

object Account {
  def props(userId: UserId, bot: Bot, dataService: DataService, localization: Localization, router: ActorRef): Props =
    Props(new Account(userId, bot, dataService, localization, router))

  case class SwitchUser(userId: UserId)

}