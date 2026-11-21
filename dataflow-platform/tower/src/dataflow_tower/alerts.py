import os
import smtplib
from collections.abc import Mapping
from dataclasses import dataclass
from email.message import EmailMessage
from typing import Self

import dagster as dg

from dataflow_tower.specs import DATASET_TAG


@dataclass(frozen=True)
class Mail:
    recipient: str
    host: str
    port: int = 1025
    sender: str = "tower@dataflow.local"
    tower_url: str = ""
    timeout: int = 30

    @classmethod
    def from_env(cls, env: Mapping[str, str] = os.environ) -> Self | None:
        recipient, host = env.get("ALERT_EMAIL"), env.get("SMTP_HOST")
        if not recipient or not host:
            return None
        return cls(recipient, host, int(env.get("SMTP_PORT", cls.port)), env.get("ALERT_FROM", cls.sender),
                   env.get("TOWER_URL", ""), int(env.get("SMTP_TIMEOUT", cls.timeout)))

    def failure(self, run: dg.DagsterRun, reason: str | None) -> EmailMessage:
        message = EmailMessage()
        message["From"] = self.sender
        message["To"] = self.recipient
        message["Subject"] = f"Run failed: {run.job_name} ({run.tags.get(DATASET_TAG, 'unknown dataset')})"
        message.set_content(f"Run {run.run_id} of {run.job_name} failed.\n\n{reason}\n\n"
                            f"{self.tower_url}/runs/{run.run_id}")
        return message

    def send(self, message: EmailMessage) -> None:
        with smtplib.SMTP(self.host, self.port, timeout=self.timeout) as smtp:
            smtp.send_message(message)


def run_failure_email() -> dg.SensorDefinition:
    @dg.run_failure_sensor(name="run_failure_email", monitor_all_code_locations=True,
                           default_status=dg.DefaultSensorStatus.RUNNING)
    def run_failure_email(context: dg.RunFailureSensorContext) -> None:
        mail = Mail.from_env()
        if mail is None:
            context.log.info("ALERT_EMAIL or SMTP_HOST unset; not emailing")
            return
        mail.send(mail.failure(context.dagster_run, context.failure_event.message))

    return run_failure_email
