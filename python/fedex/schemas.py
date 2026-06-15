import datetime as dt
import re
import typing as tp

import pydantic as pd

import data


DigitalStr = tp.Annotated[str, pd.StringConstraints(pattern=(r'^\d+$'))]
NotBlankStr = tp.Annotated[str, pd.StringConstraints(strip_whitespace=True, min_length=1)]


class Address(pd.BaseModel):
    city: NotBlankStr
    stateOrProvinceCode: data.USA_STATES


_location_state_re: re.Pattern[str] = re.compile(r'[, ]([A-Z]{2})$')


class Event(pd.BaseModel):
    model_config = pd.ConfigDict(arbitrary_types_allowed=True)

    date: dt.date
    time: dt.time
    status: NotBlankStr
    scanLocation: NotBlankStr
    delivered: bool

    _location_state: data.USA_STATES | None = None

    @property
    def location_state(self: tp.Self) -> data.USA_STATES:
        if self._location_state is not None:
            return self._location_state

        if match := _location_state_re.search(self.scanLocation):
            s = match.group(1)
            if s not in data.USA_STATE_TIMEZONES.keys():
                raise KeyError(s)
            return s  # type: ignore
        raise ValueError(f'无法提取州: "{self.scanLocation}"')

    _datetime: dt.datetime | None = None

    @property
    def datetime(self: tp.Self) -> pd.AwareDatetime:
        if self._datetime is not None:
            return self._datetime

        return dt.datetime(
            year=self.date.year,
            month=self.date.month,
            day=self.date.day,
            hour=self.time.hour,
            minute=self.time.minute,
            second=self.time.second,
            microsecond=self.time.microsecond,
            tzinfo=data.USA_STATE_TIMEZONES[self.location_state],
        )


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
    recipientAddress: Address
    scanEventList: list[Event] = pd.Field(min_length=1)


class Output(pd.BaseModel):
    packages: list[Package] = pd.Field(min_length=1)


class Response(pd.BaseModel):
    output: Output


class History(pd.BaseModel):
    datetime: pd.AwareDatetime
    status: NotBlankStr
    location: NotBlankStr
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
        recipient_city=p.recipientAddress.city,
        recipient_state=p.recipientAddress.stateOrProvinceCode,
        scan_history=history,
    )

    return r


if __name__ == '__main__':
    with open('temp.response.json', 'rb') as f:
        response = Response.model_validate_json(f.read())

    result = response_to_result(response)
