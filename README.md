# Malawi Weather

Weather data for all 28 districts of Malawi.

## What's here

| Directory | Description |
|-----------|-------------|
| `api/` | Spring Boot REST API |
| `admin/` | Admin dashboard |
| `src/` | Java desktop app |

## API

Base URL: `https://mvula.onrender.com/api/v1`

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/districts` | No | All 28 districts |
| GET | `/districts/region/{region}` | No | Districts by region |
| POST | `/developers/register` | No | Request an API key |
| GET | `/weather/{district}` | Yes | Current weather |
| GET | `/forecast/{district}` | Yes | 7-day forecast |

Authentication: `X-API-Key: your_key`

## Getting an API key

POST `/api/v1/developers/register`

```json
{
  "name": "Your Name",
  "email": "you@example.com",
  "appName": "My App",
  "appDescription": "What you are building"
}
```

Admin approves and issues the key.

## Stack

- API: Java 21 · Spring Boot 3 · PostgreSQL (Neon) · Open-Meteo
- Desktop: Java 22 · Swing · FlatLaf
- Admin: HTML/CSS/JS
- Hosting: Render · Vercel

## Districts

**Northern:** Chitipa, Karonga, Rumphi, Mzimba, Mzuzu, Likoma, Nkhata Bay

**Central:** Kasungu, Nkhotakota, Ntchisi, Dowa, Salima, Lilongwe, Mchinji, Dedza, Ntcheu

**Southern:** Mangochi, Machinga, Zomba, Chiradzulu, Blantyre, Mwanza, Thyolo, Mulanje, Phalombe, Chikwawa, Nsanje, Balaka

## License

© 2025 Pelex Technologies. All Rights Reserved. See [LICENSE](LICENSE).
