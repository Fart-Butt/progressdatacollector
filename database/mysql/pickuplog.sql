create table pickuplog
(
    player_name varchar(100)             null,
    datetime    datetime default (now()) null,
    item        varchar(100)             null,
    quantity    int                      null,
    constraint pickuplog_minecraft_players_player_name_fk
        foreign key (player_name) references minecraft_players (player_name)
);

