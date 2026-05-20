create table curios
(
    UUID         varchar(100) null,
    slot         varchar(100) null,
    item         varchar(100) null,
    display_name varchar(100) null,
    constraint curios_inventory_update_UUID_fk
        foreign key (UUID) references inventory_update (UUID)
);

