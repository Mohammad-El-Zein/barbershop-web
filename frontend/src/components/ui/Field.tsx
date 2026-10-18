"use client";

import { useId } from "react";

type Common = {
  label: string;
  error?: string;
  hint?: React.ReactNode;
  className?: string;
};

/** Labelled input with error + hint, wired with aria-invalid / aria-describedby. */
export function Field({
  label,
  error,
  hint,
  className = "",
  ...input
}: Common & React.InputHTMLAttributes<HTMLInputElement>) {
  const id = useId();
  const describedBy = [error ? `${id}-err` : null, hint ? `${id}-hint` : null].filter(Boolean).join(" ") || undefined;
  return (
    <div className={className}>
      <label htmlFor={id} className="label">
        {label}
      </label>
      <input id={id} className="input" aria-invalid={error ? true : undefined} aria-describedby={describedBy} {...input} />
      <FieldMessages id={id} error={error} hint={hint} />
    </div>
  );
}

export function TextArea({
  label,
  error,
  hint,
  className = "",
  ...input
}: Common & React.TextareaHTMLAttributes<HTMLTextAreaElement>) {
  const id = useId();
  const describedBy = [error ? `${id}-err` : null, hint ? `${id}-hint` : null].filter(Boolean).join(" ") || undefined;
  return (
    <div className={className}>
      <label htmlFor={id} className="label">
        {label}
      </label>
      <textarea
        id={id}
        className="input min-h-28 resize-y"
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
        {...input}
      />
      <FieldMessages id={id} error={error} hint={hint} />
    </div>
  );
}

export function Select({
  label,
  error,
  hint,
  className = "",
  children,
  ...input
}: Common & React.SelectHTMLAttributes<HTMLSelectElement>) {
  const id = useId();
  return (
    <div className={className}>
      <label htmlFor={id} className="label">
        {label}
      </label>
      <select id={id} className="input" aria-invalid={error ? true : undefined} {...input}>
        {children}
      </select>
      <FieldMessages id={id} error={error} hint={hint} />
    </div>
  );
}

function FieldMessages({ id, error, hint }: { id: string; error?: string; hint?: React.ReactNode }) {
  return (
    <>
      {hint && (
        <p id={`${id}-hint`} className="mt-1.5 text-xs text-muted">
          {hint}
        </p>
      )}
      {error && (
        <p id={`${id}-err`} className="mt-1.5 text-xs font-medium text-[#e0806b]" role="alert">
          {error}
        </p>
      )}
    </>
  );
}

/** Checkbox with label (label is clickable). */
export function Checkbox({
  label,
  error,
  className = "",
  ...input
}: { label: React.ReactNode; error?: string; className?: string } & React.InputHTMLAttributes<HTMLInputElement>) {
  const id = useId();
  return (
    <div className={className}>
      <div className="flex items-start gap-3">
        <input
          id={id}
          type="checkbox"
          className="mt-0.5 h-5 w-5 shrink-0 accent-[var(--gold)]"
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? `${id}-err` : undefined}
          {...input}
        />
        <label htmlFor={id} className="text-sm leading-relaxed text-ink/85">
          {label}
        </label>
      </div>
      {error && (
        <p id={`${id}-err`} className="ml-8 mt-1 text-xs font-medium text-[#e0806b]" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}
