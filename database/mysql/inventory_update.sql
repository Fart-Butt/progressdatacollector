create table inventory_update
(
    player_name     varchar(100) null,
    datetime        datetime     null,
    UUID            varchar(100) not null
        primary key,
    experiencelevel int          null,
    maxhealth       int          null,
    maxarmor        int          null,
    constraint inventory_update_minecraft_players_player_name_fk
        foreign key (player_name) references minecraft_players (player_name)
);

