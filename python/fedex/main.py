import asyncio
import datetime as dt
import contextlib as cl
import os
import re
import traceback as tb

import click
import fastapi as fa
import patchright.async_api as pr
import pydantic as pd
import uvicorn

import schemas

expect_response_re: re.Pattern[str] = re.compile(r'api.fedex.com/track/v2/shipments')

debug: bool = False


async def scrape(context: pr.BrowserContext, id: str, timeout: dt.timedelta) -> schemas.Result:
    """失败时若 debug=True 就保存响应体和页面截图"""
    body: bytes | None = None
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

                body = await (await info.value).body()
                if debug:
                    try:
                        os.makedirs(f'debug/{id}', exist_ok=True)

                        with open(f'debug/{id}/document.html', 'w') as f:
                            f.write(await page.content())
                        with open(f'debug/{id}/screenshot.jpeg', 'wb') as f:
                            f.write(await page.screenshot(type='jpeg', quality=90, timeout=1000))

                        if body is not None:
                            with open(f'debug/{id}/shipments.json', 'wb') as f:
                                f.write(body)

                    # debug 引发的异常只在此处打印，不要向外泄露
                    except Exception as debug_err:
                        tb.print_exception(debug_err)

                return schemas.response_to_result(
                    schemas.Response.model_validate_json(body, extra='ignore')
                )

    except Exception as e1:
        if debug:
            try:
                os.makedirs(f'debug/{id}', exist_ok=True)

                with open(f'debug/{id}/traceback.txt', 'w') as f:
                    f.write(''.join(tb.format_exception(e1)))

            # debug 引发的异常只在此处打印，不要向外泄露
            except Exception as debug_err:
                tb.print_exception(debug_err)

        raise e1

    finally:
        await page.close()


headless: bool = False
browser: str | None = None


@cl.asynccontextmanager
async def lifespan(app: fa.FastAPI):
    async with pr.async_playwright() as p:
        # todo: 触发反爬时需要更换浏览器
        bw = await p.chromium.launch(executable_path=browser, headless=headless)
        bc = await bw.new_context()

        app.state.browser_context = bc

        yield

        try:
            await bw.close()
        except Exception:
            pass


app = fa.FastAPI(lifespan=lifespan)


class GetResponse(pd.BaseModel):
    ok: bool
    result: schemas.Result | None = None
    traceback: str | None = None


@app.get('/{id}')
async def get(id: schemas.DigitalStr, timeout: pd.NonNegativeFloat = 30) -> GetResponse:
    """成功返回 Result 的 json，失败返回 Exception 的 traceback"""
    try:
        result = await scrape(app.state.browser_context, id, dt.timedelta(seconds=timeout))
        return GetResponse(ok=True, result=result)
    except Exception:
        return GetResponse(ok=False, traceback=tb.format_exc())


@click.command()
@click.option('--port', default=8000, help='端口')
@click.option('--headless', 'headless_', is_flag=True, default=False, help='隐藏浏览器界面')
@click.option('--browser', 'browser_', default=None, help='浏览器 exe 位置')
@click.option('--debug', 'debug_', is_flag=True, help='爬取时在 debug 目录保存网页文件')
def main(port: int, headless_: bool, browser_: str | None, debug_: bool):
    global headless, browser, debug
    headless = headless_
    browser = browser_
    debug = debug_
    uvicorn.run(app, host='127.0.0.1', port=port, timeout_graceful_shutdown=30)


if __name__ == '__main__':
    main()
