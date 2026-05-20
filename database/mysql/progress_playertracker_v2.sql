create table progress_playertracker_v2
(
    id          bigint auto_increment
        primary key,
    datetime    datetime default CURRENT_TIMESTAMP not null,
    player_name varchar(100)                       not null,
    timedelta   int                                not null,
    constraint progress_playertracker_v2_minecraft_players_player_name_fk
        foreign key (player_name) references minecraft_players (player_name)
);

