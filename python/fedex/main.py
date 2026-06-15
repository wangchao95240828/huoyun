import asyncio
import datetime as dt
import contextlib as cl
import re
import traceback as tb

import click
import fastapi as fa
import fastapi.responses as resps
import patchright.async_api as pr
import pydantic as pd
import uvicorn

import schemas

expect_response_re: re.Pattern[str] = re.compile(r'api.fedex.com/track/v2/shipments')


async def scrape(context: pr.BrowserContext, id: str, timeout: dt.timedelta) -> schemas.Response:
    page = await context.new_page()
    try:
        async with asyncio.timeout(timeout.seconds):
            async with page.expect_response(expect_response_re, timeout=0) as info:
                response: pr.Response | None = await page.goto(
                    f'https://www.fedex.com/wtrk/track/?action=track&tracknumbers={id}&locale=en_US&cntry_code=us',
                    timeout=0,
                )
                if response is None:
                    raise Exception('none response')
                if not response.ok:
                    raise Exception(f'status: {response.status}')

                response = await info.value
                return schemas.Response.model_validate_json(await response.body(), extra='ignore')

    finally:
        await page.close()


headless: bool = False


@cl.asynccontextmanager
async def lifespan(app: fa.FastAPI):
    global headless
    async with pr.async_playwright() as p:
        browser = await p.chromium.launch(headless=headless)
        context = await browser.new_context()

        app.state.browser_context = context

        yield

        try:
            await browser.close()
        except Exception:
            pass


app = fa.FastAPI(lifespan=lifespan)


@app.get('/{id}')
async def get(id: schemas.DigitalStr, timeout: pd.NonNegativeFloat = 30) -> fa.Response:
    try:
        result = schemas.response_to_result(
            await scrape(app.state.browser_context, id, dt.timedelta(seconds=timeout))
        )
        return fa.Response(
            content=result.model_dump_json(), status_code=200, media_type='application/json'
        )
    except Exception:
        return resps.PlainTextResponse(content=tb.format_exc(), status_code=500)


@click.command()
@click.option('--port', default=8000, help='端口')
@click.option('--headless', 'headless_', default=False, help='隐藏浏览器界面')
def main(port: int, headless_: bool):
    global headless
    headless = headless_
    uvicorn.run(app, host='127.0.0.1', port=port, timeout_graceful_shutdown=30)


if __name__ == '__main__':
    main()
