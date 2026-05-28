import requests

url = 'https://cgv.co.kr/cnm/bzplcCgv/0056001'
res = requests.get(url, headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36'})
if res.status_code == 200:
    print(res.text[-2000:])
else:
    print('Status:', res.status_code)
