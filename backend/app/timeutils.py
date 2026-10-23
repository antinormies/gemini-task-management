from __future__ import annotations

from datetime import UTC, date, datetime, time, timedelta
from zoneinfo import ZoneInfo


def now_utc() -> datetime:
    return datetime.now(UTC)


def iso(dt: datetime) -> str:
    return dt.astimezone(UTC).isoformat(timespec="milliseconds").replace("+00:00", "Z")


def now_iso() -> str:
    return iso(now_utc())


def parse_iso(value: str | None) -> datetime | None:
    if not value:
        return None
    text = value.strip()
    if text.endswith("Z"):
        text = text[:-1] + "+00:00"
    return datetime.fromisoformat(text).astimezone(UTC)


def tz(name: str) -> ZoneInfo:
    return ZoneInfo(name)


def local_now(name: str) -> datetime:
    return now_utc().astimezone(tz(name))


def local_day_bounds(day: date, tz_name: str) -> tuple[datetime, datetime]:
    zone = tz(tz_name)
    start = datetime.combine(day, time.min, tzinfo=zone).astimezone(UTC)
    end = (datetime.combine(day, time.min, tzinfo=zone) + timedelta(days=1)).astimezone(UTC)
    return start, end


def local_midnight_utc(day: date, tz_name: str) -> str:
    zone = tz(tz_name)
    return iso(datetime.combine(day, time.min, tzinfo=zone))


def today_local(tz_name: str) -> date:
    return local_now(tz_name).date()


def human_short_key(value: str) -> str:
    cleaned = "".join(ch for ch in value.upper() if ch.isalnum())
    return f"GTM-{cleaned[-4:]:0>4}"
