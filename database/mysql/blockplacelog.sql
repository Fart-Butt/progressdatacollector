create table blockplacelog
(
    player_name varchar(100)                       null,
    datetime    datetime default CURRENT_TIMESTAMP null,
    block       varchar(100)                       null,
    constraint blockplacelog_minecraft_players_player_name_fk
        foreign key (player_name) references minecraft_players (player_name)
);

