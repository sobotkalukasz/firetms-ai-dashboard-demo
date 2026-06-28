alter table sales_invoice
    add column payment_due_date date;

alter table sales_invoice
    add column imported_at timestamp;

update sales_invoice
set imported_at = updated_at
where imported_at is null;

create view ai_sales_invoice_view as
select
    external_id as firetms_id,
    invoice_number,
    issue_date,
    sale_date,
    payment_due_date,
    contractor_name,
    net_amount,
    gross_amount,
    currency,
    status,
    imported_at,
    updated_at
from sales_invoice;
