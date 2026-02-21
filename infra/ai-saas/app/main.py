from __future__ import annotations

import json
import os
import secrets
import uuid
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta
from typing import Any, Optional
from urllib.parse import quote

import httpx
from fastapi import (
    Depends,
    FastAPI,
    Form,
    Header,
    HTTPException,
    Request,
    Response,
    status,
)
from fastapi.responses import HTMLResponse, JSONResponse, RedirectResponse
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from fastapi.staticfiles import StaticFiles
from fastapi.templating import Jinja2Templates
from jose import JWTError, jwt
from passlib.context import CryptContext
from pydantic import BaseModel, EmailStr
from sqlalchemy import (
    Boolean,
    DateTime,
    Float,
    ForeignKey,
    Integer,
    String,
    Text,
    create_engine,
    select,
)
from sqlalchemy.orm import DeclarativeBase, Mapped, Session, mapped_column, sessionmaker


@dataclass
class Settings:
    app_name: str = os.getenv("APP_NAME", "OI Assistant")
    app_url: str = os.getenv("APP_URL", "https://ai-portal-dev.79.137.32.27.nip.io")
    database_url: str = os.getenv(
        "DATABASE_URL",
        "postgresql+psycopg2://ai_saas:ai_saas@ai-saas-postgres:5432/ai_saas",
    )
    jwt_secret: str = os.getenv("JWT_SECRET", "change-this-in-prod")
    jwt_exp_minutes: int = int(os.getenv("JWT_EXP_MINUTES", "43200"))  # 30 days
    payment_webhook_secret: str = os.getenv("PAYMENT_WEBHOOK_SECRET", "change-me")
    payment_mode: str = os.getenv("PAYMENT_MODE", "mock").lower()
    admin_api_secret: str = os.getenv("ADMIN_API_SECRET", "change-admin-secret")
    manual_payment_instructions: str = os.getenv(
        "MANUAL_PAYMENT_INSTRUCTIONS",
        "Envoie le montant par Mobile Money puis partage la reference dans le support.",
    )
    chat_ui_url: str = os.getenv("CHAT_UI_URL", "https://ai-dev.79.137.32.27.nip.io")
    whatsapp_number: str = os.getenv("WHATSAPP_NUMBER", "237691754257")
    contact_email: str = os.getenv("CONTACT_EMAIL", "support@oi.local")
    litellm_url: str = os.getenv("LITELLM_URL", "http://litellm.ai-dev.svc.cluster.local:4000")
    litellm_master_key: str = os.getenv("LITELLM_MASTER_KEY", "")
    default_model: str = os.getenv("DEFAULT_MODEL", "qwen2.5-7b")
    cookie_secure: bool = os.getenv("COOKIE_SECURE", "false").lower() == "true"


settings = Settings()


class Base(DeclarativeBase):
    pass


class User(Base):
    __tablename__ = "saas_users"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    email: Mapped[str] = mapped_column(String(255), unique=True, nullable=False)
    password_hash: Mapped[str] = mapped_column(String(255), nullable=False)
    full_name: Mapped[str] = mapped_column(String(120), nullable=False)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True, nullable=False)
    litellm_user_id: Mapped[Optional[str]] = mapped_column(String(80), nullable=True)
    litellm_api_key: Mapped[Optional[str]] = mapped_column(String(255), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(UTC), nullable=False)


class Plan(Base):
    __tablename__ = "saas_plans"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    code: Mapped[str] = mapped_column(String(40), unique=True, nullable=False)
    name: Mapped[str] = mapped_column(String(80), nullable=False)
    description: Mapped[str] = mapped_column(String(255), nullable=False)
    currency: Mapped[str] = mapped_column(String(8), nullable=False, default="XAF")
    amount: Mapped[int] = mapped_column(Integer, nullable=False)
    duration_days: Mapped[int] = mapped_column(Integer, nullable=False, default=30)
    litellm_team_alias: Mapped[str] = mapped_column(String(80), nullable=False)
    model_name: Mapped[str] = mapped_column(String(120), nullable=False)
    monthly_budget_usd: Mapped[float] = mapped_column(Float, nullable=False)
    rpm_limit: Mapped[int] = mapped_column(Integer, nullable=False)
    tpm_limit: Mapped[int] = mapped_column(Integer, nullable=False)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True, nullable=False)


class Subscription(Base):
    __tablename__ = "saas_subscriptions"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("saas_users.id"), nullable=False)
    plan_id: Mapped[str] = mapped_column(String(36), ForeignKey("saas_plans.id"), nullable=False)
    status: Mapped[str] = mapped_column(String(24), nullable=False, default="PENDING")
    start_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    end_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(UTC), nullable=False)


class Payment(Base):
    __tablename__ = "saas_payments"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=lambda: str(uuid.uuid4()))
    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("saas_users.id"), nullable=False)
    plan_id: Mapped[str] = mapped_column(String(36), ForeignKey("saas_plans.id"), nullable=False)
    provider: Mapped[str] = mapped_column(String(40), nullable=False, default="mock")
    provider_ref: Mapped[Optional[str]] = mapped_column(String(120), nullable=True)
    status: Mapped[str] = mapped_column(String(24), nullable=False, default="PENDING")
    amount: Mapped[int] = mapped_column(Integer, nullable=False)
    currency: Mapped[str] = mapped_column(String(8), nullable=False, default="XAF")
    payload_json: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=lambda: datetime.now(UTC), nullable=False)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        default=lambda: datetime.now(UTC),
        onupdate=lambda: datetime.now(UTC),
        nullable=False,
    )
    processed_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)


engine = create_engine(settings.database_url, future=True, pool_pre_ping=True)
SessionLocal = sessionmaker(bind=engine, autoflush=False, autocommit=False, expire_on_commit=False)

pwd_context = CryptContext(schemes=["pbkdf2_sha256"], deprecated="auto")
bearer_scheme = HTTPBearer(auto_error=False)

app = FastAPI(title="AI SaaS Portal", version="0.1.0")
app.mount("/static", StaticFiles(directory=os.path.join(os.path.dirname(__file__), "static")), name="static")
templates = Jinja2Templates(directory=os.path.join(os.path.dirname(__file__), "templates"))
templates.env.globals["chat_ui_url"] = settings.chat_ui_url


class SignupInput(BaseModel):
    email: EmailStr
    password: str
    full_name: str


class LoginInput(BaseModel):
    email: EmailStr
    password: str


class CheckoutInput(BaseModel):
    plan_code: str


class PaymentWebhookInput(BaseModel):
    payment_id: str
    status: str
    provider_ref: Optional[str] = None
    raw_payload: Optional[dict[str, Any]] = None


class PlanOut(BaseModel):
    code: str
    name: str
    description: str
    currency: str
    amount: int
    duration_days: int


class AdminPaymentActionInput(BaseModel):
    provider_ref: Optional[str] = None
    note: Optional[str] = None


def get_db() -> Session:
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


def hash_password(password: str) -> str:
    return pwd_context.hash(password)


def verify_password(plain: str, password_hash: str) -> bool:
    return pwd_context.verify(plain, password_hash)


def create_access_token(user_id: str) -> str:
    expires = datetime.now(UTC) + timedelta(minutes=settings.jwt_exp_minutes)
    payload = {"sub": user_id, "exp": expires}
    return jwt.encode(payload, settings.jwt_secret, algorithm="HS256")


def decode_access_token(token: str) -> str:
    try:
        payload = jwt.decode(token, settings.jwt_secret, algorithms=["HS256"])
        user_id = payload.get("sub")
        if not user_id:
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid token")
        return user_id
    except JWTError as exc:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid token") from exc


def set_auth_cookie(response: Response, token: str) -> None:
    response.set_cookie(
        key="ai_saas_token",
        value=token,
        httponly=True,
        secure=settings.cookie_secure,
        samesite="lax",
        max_age=settings.jwt_exp_minutes * 60,
        path="/",
    )


def clear_auth_cookie(response: Response) -> None:
    response.delete_cookie("ai_saas_token", path="/")


def get_current_user(
    request: Request,
    credentials: HTTPAuthorizationCredentials | None = Depends(bearer_scheme),
    db: Session = Depends(get_db),
) -> User:
    token = request.cookies.get("ai_saas_token")
    if not token and credentials:
        token = credentials.credentials
    if not token:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Authentication required")

    user_id = decode_access_token(token)
    user = db.get(User, user_id)
    if not user or not user.is_active:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="User not found")
    return user


def maybe_current_user(request: Request, db: Session) -> Optional[User]:
    token = request.cookies.get("ai_saas_token")
    if not token:
        return None
    try:
        user_id = decode_access_token(token)
    except HTTPException:
        return None
    return db.get(User, user_id)


def upsert_plans(db: Session) -> None:
    seed = [
        {
            "code": "BASIC",
            "name": "Basic",
            "description": "Usage personnel, quota limite.",
            "amount": 2500,
            "currency": "XAF",
            "duration_days": 30,
            "litellm_team_alias": "oi-basic",
            "model_name": settings.default_model,
            "monthly_budget_usd": 3,
            "rpm_limit": 20,
            "tpm_limit": 40000,
        },
        {
            "code": "PLUS",
            "name": "Plus",
            "description": "Pour usage regulier, meilleurs quotas.",
            "amount": 7000,
            "currency": "XAF",
            "duration_days": 30,
            "litellm_team_alias": "oi-plus",
            "model_name": settings.default_model,
            "monthly_budget_usd": 10,
            "rpm_limit": 40,
            "tpm_limit": 80000,
        },
        {
            "code": "PRO",
            "name": "Pro",
            "description": "Usage intensif et prioritaire.",
            "amount": 20000,
            "currency": "XAF",
            "duration_days": 30,
            "litellm_team_alias": "oi-pro",
            "model_name": settings.default_model,
            "monthly_budget_usd": 30,
            "rpm_limit": 80,
            "tpm_limit": 160000,
        },
    ]

    for item in seed:
        existing = db.scalar(select(Plan).where(Plan.code == item["code"]))
        if existing:
            existing.name = item["name"]
            existing.description = item["description"]
            existing.amount = item["amount"]
            existing.currency = item["currency"]
            existing.duration_days = item["duration_days"]
            existing.litellm_team_alias = item["litellm_team_alias"]
            existing.model_name = item["model_name"]
            existing.monthly_budget_usd = item["monthly_budget_usd"]
            existing.rpm_limit = item["rpm_limit"]
            existing.tpm_limit = item["tpm_limit"]
            existing.is_active = True
            continue

        db.add(Plan(**item))

    db.commit()


def require_plan(db: Session, plan_code: str) -> Plan:
    plan = db.scalar(select(Plan).where(Plan.code == plan_code.upper(), Plan.is_active.is_(True)))
    if not plan:
        raise HTTPException(status_code=404, detail="Plan not found")
    return plan


async def litellm_request(method: str, path: str, payload: Optional[dict[str, Any]] = None) -> dict[str, Any] | list[Any]:
    if not settings.litellm_master_key:
        raise RuntimeError("LITELLM_MASTER_KEY is missing")

    url = f"{settings.litellm_url.rstrip('/')}{path}"
    headers = {
        "Authorization": f"Bearer {settings.litellm_master_key}",
        "Content-Type": "application/json",
    }

    async with httpx.AsyncClient(timeout=20) as client:
        response = await client.request(method, url, headers=headers, json=payload)
        if response.status_code >= 400:
            raise RuntimeError(f"LiteLLM {method} {path} failed: {response.status_code} - {response.text}")
        if not response.content:
            return {}
        return response.json()


async def ensure_litellm_user(user: User) -> str:
    litellm_user_id = user.litellm_user_id or f"saas_{user.id.replace('-', '')[:24]}"

    payload = {
        "user_id": litellm_user_id,
        "user_email": user.email,
        "user_role": "internal_user",
    }
    try:
        await litellm_request("POST", "/user/new", payload)
    except RuntimeError as exc:
        msg = str(exc).lower()
        # User may already exist; continue.
        if "already" not in msg and "exists" not in msg and "400" not in msg:
            raise

    return litellm_user_id


async def resolve_team_id(team_alias: str) -> Optional[str]:
    raw = await litellm_request("GET", "/team/list")
    teams = raw if isinstance(raw, list) else raw.get("teams", [])
    for team in teams:
        if team.get("team_alias") == team_alias:
            return team.get("team_id")
    return None


def mask_key(value: Optional[str]) -> Optional[str]:
    if not value:
        return None
    if len(value) < 10:
        return "********"
    return f"{value[:6]}...{value[-4:]}"


def is_manual_payment_mode() -> bool:
    return settings.payment_mode in {"manual", "offline"}


def require_admin_secret(x_admin_secret: Optional[str]) -> None:
    if x_admin_secret != settings.admin_api_secret:
        raise HTTPException(status_code=401, detail="Invalid admin secret")


async def activate_subscription(db: Session, user: User, plan: Plan, payment: Payment) -> str:
    litellm_user_id = await ensure_litellm_user(user)
    team_id = await resolve_team_id(plan.litellm_team_alias)

    payload = {
        "user_id": litellm_user_id,
        "models": [plan.model_name],
        "max_budget": plan.monthly_budget_usd,
        "budget_duration": "30d",
        "duration": f"{plan.duration_days}d",
        "rpm_limit": plan.rpm_limit,
        "tpm_limit": plan.tpm_limit,
    }
    if team_id:
        payload["team_id"] = team_id

    generated = await litellm_request("POST", "/key/generate", payload)
    api_key = generated.get("key")
    if not api_key:
        raise RuntimeError("LiteLLM key was not returned")

    now = datetime.now(UTC)
    # expire previous active subscriptions
    old_subs = db.scalars(
        select(Subscription).where(Subscription.user_id == user.id, Subscription.status == "ACTIVE")
    ).all()
    for old in old_subs:
        old.status = "EXPIRED"

    active_sub = Subscription(
        user_id=user.id,
        plan_id=plan.id,
        status="ACTIVE",
        start_at=now,
        end_at=now + timedelta(days=plan.duration_days),
    )

    user.litellm_user_id = litellm_user_id
    user.litellm_api_key = api_key

    payment.status = "SUCCEEDED"
    payment.processed_at = now

    db.add(active_sub)
    db.commit()
    return api_key


async def handle_payment_status(db: Session, payload: PaymentWebhookInput) -> Payment:
    payment = db.get(Payment, payload.payment_id)
    if not payment:
        raise HTTPException(status_code=404, detail="Payment not found")

    # idempotent retries
    if payment.status in {"SUCCEEDED", "FAILED"}:
        return payment

    payment.provider_ref = payload.provider_ref or payment.provider_ref
    payment.payload_json = json.dumps(payload.raw_payload or {}, ensure_ascii=True)

    if payload.status.upper() == "SUCCESS":
        user = db.get(User, payment.user_id)
        plan = db.get(Plan, payment.plan_id)
        if not user or not plan:
            raise HTTPException(status_code=500, detail="User/plan missing")
        await activate_subscription(db, user, plan, payment)
    else:
        payment.status = "FAILED"
        payment.processed_at = datetime.now(UTC)
        db.commit()

    return payment


@app.on_event("startup")
def on_startup() -> None:
    Base.metadata.create_all(bind=engine)
    with SessionLocal() as db:
        upsert_plans(db)


@app.get("/health")
def health() -> dict[str, bool]:
    return {"ok": True}


@app.get("/", response_class=HTMLResponse)
def home(request: Request, db: Session = Depends(get_db)) -> HTMLResponse:
    plans = db.scalars(select(Plan).where(Plan.is_active.is_(True)).order_by(Plan.amount.asc())).all()
    user = maybe_current_user(request, db)
    offers: list[dict[str, Any]] = []
    for plan in plans:
        price_value = f"{plan.amount:,}".replace(",", " ")
        message = (
            f"Bonjour, je veux souscrire a l'offre {plan.name} "
            f"({price_value} {plan.currency} / {plan.duration_days} jours)."
        )
        offers.append(
            {
                "code": plan.code,
                "name": plan.name,
                "description": plan.description,
                "amount": plan.amount,
                "currency": plan.currency,
                "duration_days": plan.duration_days,
                "model_name": plan.model_name,
                "rpm_limit": plan.rpm_limit,
                "tpm_limit": plan.tpm_limit,
                "monthly_budget_usd": plan.monthly_budget_usd,
                "whatsapp_url": f"https://wa.me/{settings.whatsapp_number}?text={quote(message)}",
            }
        )
    return templates.TemplateResponse(
        "index.html",
        {
            "request": request,
            "app_name": settings.app_name,
            "plans": plans,
            "offers": offers,
            "user": user,
            "hide_topbar": True,
            "fluid_layout": True,
            "payment_mode": settings.payment_mode,
            "manual_payment_instructions": settings.manual_payment_instructions,
            "whatsapp_number": settings.whatsapp_number,
            "contact_email": settings.contact_email,
        },
    )


@app.get("/signup", response_class=HTMLResponse)
def signup_page(request: Request, db: Session = Depends(get_db)) -> HTMLResponse:
    return templates.TemplateResponse(
        "signup.html",
        {
            "request": request,
            "app_name": settings.app_name,
            "error": None,
            "user": maybe_current_user(request, db),
        },
    )


@app.post("/signup", response_class=HTMLResponse)
def signup_action(
    request: Request,
    full_name: str = Form(...),
    email: EmailStr = Form(...),
    password: str = Form(...),
    db: Session = Depends(get_db),
) -> Response:
    existing = db.scalar(select(User).where(User.email == email.lower()))
    if existing:
        return templates.TemplateResponse(
            "signup.html",
            {
                "request": request,
                "app_name": settings.app_name,
                "error": "Cet email est deja utilise.",
                "user": None,
            },
            status_code=400,
        )

    if len(password) < 8:
        return templates.TemplateResponse(
            "signup.html",
            {
                "request": request,
                "app_name": settings.app_name,
                "error": "Mot de passe minimum: 8 caracteres.",
                "user": None,
            },
            status_code=400,
        )

    user = User(email=email.lower(), full_name=full_name.strip(), password_hash=hash_password(password))
    db.add(user)
    db.commit()

    token = create_access_token(user.id)
    response = RedirectResponse(url="/dashboard", status_code=status.HTTP_303_SEE_OTHER)
    set_auth_cookie(response, token)
    return response


@app.get("/login", response_class=HTMLResponse)
def login_page(request: Request, db: Session = Depends(get_db)) -> HTMLResponse:
    return templates.TemplateResponse(
        "login.html",
        {
            "request": request,
            "app_name": settings.app_name,
            "error": None,
            "user": maybe_current_user(request, db),
        },
    )


@app.post("/login", response_class=HTMLResponse)
def login_action(
    request: Request,
    email: EmailStr = Form(...),
    password: str = Form(...),
    db: Session = Depends(get_db),
) -> Response:
    user = db.scalar(select(User).where(User.email == email.lower()))
    if not user or not verify_password(password, user.password_hash):
        return templates.TemplateResponse(
            "login.html",
            {
                "request": request,
                "app_name": settings.app_name,
                "error": "Identifiants invalides.",
                "user": None,
            },
            status_code=401,
        )

    token = create_access_token(user.id)
    response = RedirectResponse(url="/dashboard", status_code=status.HTTP_303_SEE_OTHER)
    set_auth_cookie(response, token)
    return response


@app.post("/logout")
def logout_action() -> Response:
    response = RedirectResponse(url="/", status_code=status.HTTP_303_SEE_OTHER)
    clear_auth_cookie(response)
    return response


@app.get("/dashboard", response_class=HTMLResponse)
def dashboard(request: Request, current_user: User = Depends(get_current_user), db: Session = Depends(get_db)) -> HTMLResponse:
    active_sub = db.scalar(
        select(Subscription)
        .where(Subscription.user_id == current_user.id, Subscription.status == "ACTIVE")
        .order_by(Subscription.created_at.desc())
    )

    current_plan: Optional[Plan] = None
    if active_sub:
        current_plan = db.get(Plan, active_sub.plan_id)

    plans = db.scalars(select(Plan).where(Plan.is_active.is_(True)).order_by(Plan.amount.asc())).all()
    payments = db.scalars(
        select(Payment).where(Payment.user_id == current_user.id).order_by(Payment.created_at.desc())
    ).all()

    return templates.TemplateResponse(
        "dashboard.html",
        {
            "request": request,
            "app_name": settings.app_name,
            "user": current_user,
            "plans": plans,
            "active_sub": active_sub,
            "current_plan": current_plan,
            "payments": payments,
            "api_key_masked": mask_key(current_user.litellm_api_key),
            "api_key_full": current_user.litellm_api_key,
            "api_url": settings.litellm_url,
            "payment_mode": settings.payment_mode,
            "manual_payment_instructions": settings.manual_payment_instructions,
        },
    )


@app.post("/checkout/start")
def checkout_start(
    plan_code: str = Form(...),
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> Response:
    plan = require_plan(db, plan_code)
    provider = "manual" if is_manual_payment_mode() else "mock"
    payment = Payment(
        user_id=current_user.id,
        plan_id=plan.id,
        provider=provider,
        status="PENDING",
        amount=plan.amount,
        currency=plan.currency,
    )
    db.add(payment)
    db.commit()
    if is_manual_payment_mode():
        return RedirectResponse(url=f"/checkout/manual/{payment.id}", status_code=status.HTTP_303_SEE_OTHER)
    return RedirectResponse(url=f"/checkout/mock/{payment.id}", status_code=status.HTTP_303_SEE_OTHER)


@app.get("/checkout/mock/{payment_id}", response_class=HTMLResponse)
def checkout_mock_page(
    payment_id: str,
    request: Request,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> HTMLResponse:
    payment = db.get(Payment, payment_id)
    if not payment or payment.user_id != current_user.id:
        raise HTTPException(status_code=404, detail="Payment not found")
    plan = db.get(Plan, payment.plan_id)
    return templates.TemplateResponse(
        "mock_checkout.html",
        {
            "request": request,
            "app_name": settings.app_name,
            "user": current_user,
            "payment": payment,
            "plan": plan,
        },
    )


@app.get("/checkout/manual/{payment_id}", response_class=HTMLResponse)
def checkout_manual_page(
    payment_id: str,
    request: Request,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> HTMLResponse:
    payment = db.get(Payment, payment_id)
    if not payment or payment.user_id != current_user.id:
        raise HTTPException(status_code=404, detail="Payment not found")
    plan = db.get(Plan, payment.plan_id)
    return templates.TemplateResponse(
        "manual_checkout.html",
        {
            "request": request,
            "app_name": settings.app_name,
            "user": current_user,
            "payment": payment,
            "plan": plan,
            "manual_payment_instructions": settings.manual_payment_instructions,
        },
    )


@app.post("/checkout/mock/{payment_id}/complete")
async def checkout_mock_complete(
    payment_id: str,
    status_value: str = Form(...),
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> Response:
    payment = db.get(Payment, payment_id)
    if not payment or payment.user_id != current_user.id:
        raise HTTPException(status_code=404, detail="Payment not found")

    payload = PaymentWebhookInput(
        payment_id=payment_id,
        status="SUCCESS" if status_value.lower() == "success" else "FAILED",
        provider_ref=f"mock-{secrets.token_hex(6)}",
        raw_payload={"source": "mock-ui", "status": status_value},
    )
    await handle_payment_status(db, payload)
    return RedirectResponse(url="/dashboard", status_code=status.HTTP_303_SEE_OTHER)


@app.get("/api/plans", response_model=list[PlanOut])
def list_plans(db: Session = Depends(get_db)) -> list[PlanOut]:
    plans = db.scalars(select(Plan).where(Plan.is_active.is_(True)).order_by(Plan.amount.asc())).all()
    return [
        PlanOut(
            code=p.code,
            name=p.name,
            description=p.description,
            currency=p.currency,
            amount=p.amount,
            duration_days=p.duration_days,
        )
        for p in plans
    ]


@app.post("/api/auth/signup")
def api_signup(payload: SignupInput, db: Session = Depends(get_db)) -> dict[str, str]:
    existing = db.scalar(select(User).where(User.email == payload.email.lower()))
    if existing:
        raise HTTPException(status_code=409, detail="Email already used")
    if len(payload.password) < 8:
        raise HTTPException(status_code=400, detail="Password too short")

    user = User(
        email=payload.email.lower(),
        full_name=payload.full_name.strip(),
        password_hash=hash_password(payload.password),
    )
    db.add(user)
    db.commit()

    token = create_access_token(user.id)
    return {"access_token": token}


@app.post("/api/auth/login")
def api_login(payload: LoginInput, db: Session = Depends(get_db)) -> dict[str, str]:
    user = db.scalar(select(User).where(User.email == payload.email.lower()))
    if not user or not verify_password(payload.password, user.password_hash):
        raise HTTPException(status_code=401, detail="Invalid credentials")
    token = create_access_token(user.id)
    return {"access_token": token}


@app.get("/api/me")
def api_me(current_user: User = Depends(get_current_user), db: Session = Depends(get_db)) -> dict[str, Any]:
    active_sub = db.scalar(
        select(Subscription)
        .where(Subscription.user_id == current_user.id, Subscription.status == "ACTIVE")
        .order_by(Subscription.created_at.desc())
    )
    plan = db.get(Plan, active_sub.plan_id) if active_sub else None
    return {
        "id": current_user.id,
        "email": current_user.email,
        "full_name": current_user.full_name,
        "active_subscription": plan.code if plan else None,
        "api_key_masked": mask_key(current_user.litellm_api_key),
    }


@app.post("/api/checkout/create")
def api_checkout_create(
    payload: CheckoutInput,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> dict[str, str]:
    plan = require_plan(db, payload.plan_code)
    provider = "manual" if is_manual_payment_mode() else "mock"
    payment = Payment(
        user_id=current_user.id,
        plan_id=plan.id,
        provider=provider,
        status="PENDING",
        amount=plan.amount,
        currency=plan.currency,
    )
    db.add(payment)
    db.commit()
    checkout_path = "/checkout/manual" if is_manual_payment_mode() else "/checkout/mock"
    return {
        "payment_id": payment.id,
        "checkout_url": f"{settings.app_url}{checkout_path}/{payment.id}",
        "status": payment.status,
        "provider": provider,
    }


@app.post("/api/payments/webhook/mock")
async def api_webhook_mock(
    payload: PaymentWebhookInput,
    x_webhook_secret: Optional[str] = Header(default=None, alias="X-Webhook-Secret"),
    db: Session = Depends(get_db),
) -> dict[str, str]:
    if x_webhook_secret != settings.payment_webhook_secret:
        raise HTTPException(status_code=401, detail="Invalid webhook secret")

    payment = await handle_payment_status(db, payload)
    return {"payment_id": payment.id, "status": payment.status}


@app.get("/api/admin/payments/pending")
def api_admin_pending_payments(
    x_admin_secret: Optional[str] = Header(default=None, alias="X-Admin-Secret"),
    db: Session = Depends(get_db),
) -> list[dict[str, Any]]:
    require_admin_secret(x_admin_secret)
    pending = db.scalars(select(Payment).where(Payment.status == "PENDING").order_by(Payment.created_at.asc())).all()
    items: list[dict[str, Any]] = []
    for payment in pending:
        user = db.get(User, payment.user_id)
        plan = db.get(Plan, payment.plan_id)
        items.append(
            {
                "payment_id": payment.id,
                "email": user.email if user else None,
                "plan": plan.code if plan else None,
                "amount": payment.amount,
                "currency": payment.currency,
                "provider": payment.provider,
                "created_at": payment.created_at.isoformat() if payment.created_at else None,
            }
        )
    return items


@app.post("/api/admin/payments/{payment_id}/approve")
async def api_admin_approve_payment(
    payment_id: str,
    payload: AdminPaymentActionInput,
    x_admin_secret: Optional[str] = Header(default=None, alias="X-Admin-Secret"),
    db: Session = Depends(get_db),
) -> dict[str, str]:
    require_admin_secret(x_admin_secret)
    webhook_payload = PaymentWebhookInput(
        payment_id=payment_id,
        status="SUCCESS",
        provider_ref=payload.provider_ref or f"manual-{secrets.token_hex(6)}",
        raw_payload={"source": "admin-approve", "note": payload.note or ""},
    )
    payment = await handle_payment_status(db, webhook_payload)
    return {"payment_id": payment.id, "status": payment.status}


@app.post("/api/admin/payments/{payment_id}/reject")
async def api_admin_reject_payment(
    payment_id: str,
    payload: AdminPaymentActionInput,
    x_admin_secret: Optional[str] = Header(default=None, alias="X-Admin-Secret"),
    db: Session = Depends(get_db),
) -> dict[str, str]:
    require_admin_secret(x_admin_secret)
    webhook_payload = PaymentWebhookInput(
        payment_id=payment_id,
        status="FAILED",
        provider_ref=payload.provider_ref or f"manual-reject-{secrets.token_hex(6)}",
        raw_payload={"source": "admin-reject", "note": payload.note or ""},
    )
    payment = await handle_payment_status(db, webhook_payload)
    return {"payment_id": payment.id, "status": payment.status}


@app.get("/api/subscription/status")
def api_subscription_status(current_user: User = Depends(get_current_user), db: Session = Depends(get_db)) -> JSONResponse:
    active_sub = db.scalar(
        select(Subscription)
        .where(Subscription.user_id == current_user.id, Subscription.status == "ACTIVE")
        .order_by(Subscription.created_at.desc())
    )
    if not active_sub:
        return JSONResponse({"status": "NONE"})

    plan = db.get(Plan, active_sub.plan_id)
    return JSONResponse(
        {
            "status": "ACTIVE",
            "plan": plan.code if plan else None,
            "end_at": active_sub.end_at.isoformat() if active_sub.end_at else None,
            "api_key_masked": mask_key(current_user.litellm_api_key),
        }
    )
