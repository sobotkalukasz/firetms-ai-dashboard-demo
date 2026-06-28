alter table sales_invoice
    add column ksef_number varchar(255);

alter table sales_invoice
    add column outstanding_to_pay decimal(19, 2);
