import datetime as dt
import re
import typing as tp

import pydantic as pd

import data


DigitalStr = tp.Annotated[str, pd.StringConstraints(pattern=(r'^\d+$'))]
NotBlankStr = tp.Annotated[str, pd.StringConstraints(strip_whitespace=True, min_length=1)]
StripedStr = tp.Annotated[str, pd.StringConstraints(strip_whitespace=True)]


class Address(pd.BaseModel):
    city: NotBlankStr
    stateOrProvinceCode: data.USA_STATES


_location_state_re: re.Pattern[str] = re.compile(r'[, ](?P<state>[A-Z]{2})$')
_gmtoffset_re: re.Pattern[str] = re.compile(r'^(?P<sign>[-+]?)(?P<hour>\d{2}):(?P<minute>\d{2})$')


class Event(pd.BaseModel):
    model_config = pd.ConfigDict(arbitrary_types_allowed=True)

    date: dt.date
    time: dt.time
    status: NotBlankStr
    scanLocation: StripedStr
    delivered: bool
    gmtOffset: StripedStr

    _datetime: dt.datetime | None = None

    @property
    def datetime(self: tp.Self) -> pd.AwareDatetime:
        if self._datetime is not None:
            return self._datetime

        tzinfo: dt.tzinfo

        # 先尝试 gmtOffset，再尝试 scanLocation
        if match := _gmtoffset_re.search(self.gmtOffset):
            sign: tp.Literal['', '-', '+'] = match.group('sign')  # type: ignore

            hour = int(match.group('hour'))
            if hour < 0 or hour > 23:
                raise ValueError(f'hour={hour}')

            minute = int(match.group('minute'))
            if minute < 0 or minute > 59:
                raise ValueError(f'minuete={minute}')

            total_seconds = hour * 3600 + minute * 60
            if sign == '-':
                total_seconds = -total_seconds
            tzinfo = dt.timezone(dt.timedelta(seconds=total_seconds))

        elif match := _location_state_re.search(self.scanLocation):
            state = match.group('state')
            if state not in data.USA_STATE_TIMEZONES:
                raise KeyError(state)
            tzinfo = data.USA_STATE_TIMEZONES[state]

        else:
            raise ValueError('无法从 gmtOffset 或 scanLocation 提取时区')

        self._datetime = dt.datetime(
            year=self.date.year,
            month=self.date.month,
            day=self.date.day,
            hour=self.time.hour,
            minute=self.time.minute,
            second=self.time.second,
            microsecond=self.time.microsecond,
            tzinfo=tzinfo,
        )
        return self._datetime


class Package(pd.BaseModel):
    trackingNbr: DigitalStr
    lastScanStatus: NotBlankStr
    serviceDesc: NotBlankStr
    shipDt: pd.AwareDatetime
    lastScanDateTime: pd.AwareDatetime
    receivedByNm: NotBlankStr
    pkgKgsWgt: pd.NonNegativeFloat
    pkgLbsWgt: pd.NonNegativeFloat
    shipperAddress: Address
    destLocationAddress: Address
    scanEventList: list[Event] = pd.Field(min_length=1)


class Output(pd.BaseModel):
    packages: list[Package] = pd.Field(min_length=1)


class Response(pd.BaseModel):
    output: Output


class History(pd.BaseModel):
    datetime: pd.AwareDatetime
    status: NotBlankStr
    location: StripedStr
    delivered: bool


class Result(pd.BaseModel):
    tracking_number: DigitalStr
    status: NotBlankStr
    service: NotBlankStr
    ship_datetime: pd.AwareDatetime
    delivery_datetime: pd.AwareDatetime
    signed_by: NotBlankStr
    weight_kg: pd.NonNegativeFloat
    weight_lb: pd.NonNegativeFloat
    shipper_city: NotBlankStr
    shipper_state: data.USA_STATES
    recipient_city: NotBlankStr
    recipient_state: data.USA_STATES
    scan_history: list[History] = pd.Field(min_length=1)


def response_to_result(response: Response, /) -> Result:
    p = response.output.packages[0]
    history = [
        History(
            datetime=h.datetime, status=h.status, location=h.scanLocation, delivered=h.delivered
        )
        for h in p.scanEventList
    ]
    history.sort(key=lambda h: h.datetime)
    r = Result(
        tracking_number=p.trackingNbr,
        status=p.lastScanStatus,
        service=p.serviceDesc,
        ship_datetime=p.shipDt,
        delivery_datetime=p.lastScanDateTime,
        signed_by=p.receivedByNm,
        weight_kg=p.pkgKgsWgt,
        weight_lb=p.pkgLbsWgt,
        shipper_city=p.shipperAddress.city,
        shipper_state=p.shipperAddress.stateOrProvinceCode,
        recipient_city=p.destLocationAddress.city,
        recipient_state=p.destLocationAddress.stateOrProvinceCode,
        scan_history=history,
    )

    return r


if __name__ == '__main__':
    import pathlib as pl
    import traceback as tb

    dir = pl.Path('debug')
    for f in dir.rglob('shipments.json'):
        print(f'\n========== {f.parent} ==========\n')
        try:
            response = Response.model_validate_json(f.read_bytes())
            result = response_to_result(response)
        except Exception:
            tb.print_exc()
            continue
