import type { components } from "./schema";

/** Short names for the generated API schemas. */
type S = components["schemas"];

export type Booking = S["BookingView"];
export type AdminBooking = S["BookingAdminView"];
export type Service = S["ServiceView"];
export type StaffMember = S["StaffView"];
export type DayView = S["DayView"];
export type BarberColumn = S["BarberColumn"];
export type Slot = S["SlotView"];
export type AnySlot = S["AnySlotView"];
export type PricePreview = S["PricePreview"];
export type PriceView = S["PriceView"];
export type Money = S["MoneyView"];
export type User = S["UserView"];
export type Session = S["SessionResponse"];
export type Shop = S["TenantView"];
export type BookingRules = S["BookingRules"];
export type Loyalty = S["LoyaltyView"];
export type PaymentPage = S["PaymentPageView"];
export type Decision = S["DecisionView"];
export type CustomerRow = S["CustomerRow"];
export type Stats = S["StatsView"];
export type Policy = S["PolicyDto"];
export type StaffAdmin = S["StaffAdminView"];
export type ServiceAdmin = S["ServiceAdminView"];
export type WorkingHoursEntry = S["WorkingHoursEntry"];
export type TimeOff = S["TimeOffView"];
export type ShopProfile = S["ProfileDto"];
export type OpeningRange = S["OpeningRange"];

/** Booking lifecycle statuses (backend BookingStatus). */
export type BookingStatus =
  | "PENDING_BARBER"
  | "AWAITING_PAYMENT"
  | "CONFIRMED"
  | "COMPLETED"
  | "DECLINED"
  | "EXPIRED"
  | "CANCELLED"
  | "NO_SHOW";

export type Role = "CUSTOMER" | "BARBER" | "ADMIN";
