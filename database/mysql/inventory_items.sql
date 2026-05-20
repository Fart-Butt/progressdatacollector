create table inventory_items
(
    UUID         varchar(100) null,
    type         int          null,
    slot         int          null,
    item         varchar(100) null,
    display_name varchar(100) null,
    constraint inventory_items_inventory_update_UUID_fk
        foreign key (UUID) references inventory_update (UUID)
);

