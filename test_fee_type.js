const http = require('http');

const token = 'eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOiI5ZTdiZTM5NC00MTg2LTRjY2ItODQwMy0yODk5OTJjOTBjOTQiLCJ0ZW5hbnRJZCI6Ijk0NzYyZDFkLTFjNDktNGY3Ni1iOGYxLTAyNTJiODE5ZjE4ZCIsInRlbmFudENvZGUiOiJ4cXQiLCJ1c2VybmFtZSI6ImFkbWluIiwiZGlzcGxheU5hbWUiOiLns7vnu5_nrqHnkIblkZgiLCJyb2xlcyI6WyJBRE1JTiJdLCJwZXJtaXNzaW9ucyI6WyJhZG1pbi5hdWRpdC5yZWFkIiwiYWRtaW4ucGVybWlzc2lvbi5yZWFkIiwiYWRtaW4ucGVybWlzc2lvbi53cml0ZSIsImFkbWluLnJvbGUucmVhZCIsImFkbWluLnJvbGUud3JpdGUiLCJhZG1pbi51c2VyLnJlYWQiLCJhZG1pbi51c2VyLndyaXRlIiwiYXV0aC5wcm9maWxlLnJlYWQiLCJidXNpbmVzcy5mbG93LnJlYWQiLCJidXNpbmVzcy5mbG93LndyaXRlIiwiZmluYW5jZS5hY2NvdW50LnJlYWQiLCJmaW5hbmNlLmFjY291bnQud3JpdGUiLCJmaW5hbmNlLmFkanVzdC5hcHByb3ZlIiwiZmluYW5jZS5iaWxsLmltcG9ydCIsImZpbmFuY2UuaW52b2ljZS5yZWFkIiwiZmluYW5jZS5pbnZvaWNlLndyaXRlIiwiZmluYW5jZS5sZWRnZXIucG9zdCIsImZpbmFuY2UubGVkZ2VyLnJlYWQiLCJmaW5hbmNlLnBheWFibGUucmVhZCIsImZpbmFuY2UucGF5YWJsZS53cml0ZSIsImZpbmFuY2UucmF0ZS5yZWFkIiwiZmluYW5jZS5yYXRlLndyaXRlIiwiZmluYW5jZS5yZWNlaXZhYmxlLnJlYWQiLCJmaW5hbmNlLnJlY2VpdmFibGUud3JpdGUiLCJmaW5hbmNlLnJlY29uY2lsZS5yZXZpZXciLCJmbG93LmRvY3VtZW50LnJlYWQiLCJmbG93LmRvY3VtZW50LndyaXRlIiwiZmxvdy5zZWxsZXIucmVhZCIsImZsb3cuc2VsbGVyLndyaXRlIiwib3BlcmF0aW9uLmRlbGl2ZXJ5X3F1b3RlLndyaXRlIiwib3BlcmF0aW9uLm9yZGVyLnJlYWQiLCJvcGVyYXRpb24ub3JkZXIud3JpdGUiLCJ3YXJlaG91c2Uuc2Nhbi53cml0ZSJdLCJleHAiOjE3NzgzMTkxNTUsImp0aSI6IjYxNDdiYWZmLTYyOGYtNDIxZS1hMmRlLWM2MjM3MzYxZDVmMCJ9.m4eCvbw0TvBP_e_AXY9HY6qR4ucdfVArnpXWpSYjPNc';

const options = {
    hostname: 'localhost',
    port: 8888,
    path: '/api/finance-fee-types',
    method: 'GET',
    headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
    }
};

const req = http.request(options, (res) => {
    console.log(`Status: ${res.statusCode}`);
    console.log(`Headers: ${JSON.stringify(res.headers)}`);
    
    let body = '';
    res.on('data', (chunk) => {
        body += chunk;
    });
    
    res.on('end', () => {
        console.log('Body:', body);
    });
});

req.on('error', (e) => {
    console.error(`Error: ${e.message}`);
});

req.end();