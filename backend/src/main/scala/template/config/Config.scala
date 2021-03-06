package template.config

import template.email.EmailConfig
import template.http.HttpConfig
import template.fileRetrieval.FSConfig
import template.infrastructure.DBConfig
import template.passwordreset.PasswordResetConfig
import template.user.UserConfig

/**
  * Maps to the `application.conf` file. Configuration for all modules of the application.
  */
case class Config(
    env: Env,
    db: DBConfig,
    api: HttpConfig,
    email: EmailConfig,
    passwordReset: PasswordResetConfig,
    user: UserConfig,
    fsService: FSConfig
)
