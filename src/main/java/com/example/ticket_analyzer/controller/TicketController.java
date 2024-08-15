package com.example.ticket_analyzer.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    @GetMapping("/analyze")
    public String analyzeTickets() throws IOException {
        String filePath = "src/main/resources/tickets.json";
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode rootNode = objectMapper.readTree(new File(filePath));
        JsonNode ticketsNode = rootNode.get("tickets");

        // Проверка на существование узла с билетами
        if (ticketsNode == null || !ticketsNode.isArray()) {
            throw new IOException("Tickets node is missing or not an array");
        }

        List<Ticket> tickets = new ArrayList<>();
        for (JsonNode ticketNode : ticketsNode) {
            String origin = ticketNode.get("origin").asText();
            String destination = ticketNode.get("destination").asText();
            String carrier = ticketNode.get("carrier").asText();
            int price = ticketNode.get("price").asInt();
            String departureTime = ticketNode.get("departure_time").asText();
            String arrivalTime = ticketNode.get("arrival_time").asText();

            // Преобразование времени вылета и прилёта в минуты
            int flightDuration = calculateFlightDuration(departureTime, arrivalTime);

            tickets.add(new Ticket(origin, destination, carrier, price, departureTime, arrivalTime, flightDuration));
        }

        // Фильтрация билетов между Владивостоком и Тель-Авивом
        List<Ticket> vlavivTickets = tickets.stream()
                .filter(ticket -> ticket.getOrigin().equalsIgnoreCase("VVO")
                        && ticket.getDestination().equalsIgnoreCase("TLV"))
                .collect(Collectors.toList());

        // Проверка на наличие подходящих билетов
        if (vlavivTickets.isEmpty()) {
            return "Нет билетов между Владивостоком и Тель-Авивом.";
        }

        // Вычисление минимального времени полета для каждого авиаперевозчика
        Map<String, Integer> minFlightTimeByCarrier = vlavivTickets.stream()
                .collect(Collectors.groupingBy(Ticket::getCarrier,
                        Collectors.collectingAndThen(
                                Collectors.minBy(Comparator.comparingInt(Ticket::getFlightDuration)),
                                min -> min.isPresent() ? min.get().getFlightDuration() : 0
                        )
                ));

        // Вычисление средней цены и медианы
        List<Integer> prices = vlavivTickets.stream().map(Ticket::getPrice).sorted().collect(Collectors.toList());
        double averagePrice = prices.stream().mapToInt(Integer::intValue).average().orElse(0.0);
        double medianPrice;
        int size = prices.size();

        // Проверка на пустой список цен
        if (size == 0) {
            medianPrice = 0.0;
        } else if (size % 2 == 0) {
            medianPrice = (prices.get(size / 2 - 1) + prices.get(size / 2)) / 2.0;
        } else {
            medianPrice = prices.get(size / 2);
        }


        StringBuilder result = new StringBuilder();
        result.append("Минимальное время полета для каждого авиаперевозчика:\n");
        for (Map.Entry<String, Integer> entry : minFlightTimeByCarrier.entrySet()) {
            result.append(entry.getKey()).append(": ").append(entry.getValue()).append(" минут\n");
        }
        result.append("\nРазница между средней ценой и медианой: ").append(averagePrice - medianPrice).append(" рублей");

        return result.toString();
    }
    private int calculateFlightDuration(String departureTime, String arrivalTime) {

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("H:mm");

        try {
            LocalTime departure = LocalTime.parse(departureTime, formatter);
            LocalTime arrival = LocalTime.parse(arrivalTime, formatter);
            return (int) ChronoUnit.MINUTES.between(departure, arrival);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Failed to parse time: " + e.getMessage(), e);
        }
    }

    static class Ticket {
        private final String origin;
        private final String destination;
        private final String carrier;
        private final int price;
        private final String departureTime;
        private final String arrivalTime;

        public Ticket(String origin, String destination, String carrier, int price, String departureTime, String arrivalTime, int flightDuration) {
            this.origin = origin;
            this.destination = destination;
            this.carrier = carrier;
            this.price = price;
            this.departureTime = departureTime;
            this.arrivalTime = arrivalTime;
        }

        public String getOrigin() {
            return origin;
        }

        public String getDestination() {
            return destination;
        }

        public String getCarrier() {
            return carrier;
        }

        public int getPrice() {
            return price;
        }

        public int getFlightDuration() {
            String[] depParts = departureTime.split(":");
            String[] arrParts = arrivalTime.split(":");

            int depHour = Integer.parseInt(depParts[0]);
            int depMinute = Integer.parseInt(depParts[1]);

            int arrHour = Integer.parseInt(arrParts[0]);
            int arrMinute = Integer.parseInt(arrParts[1]);

            int durationMinutes = (arrHour * 60 + arrMinute) - (depHour * 60 + depMinute);
            if (durationMinutes < 0) {
                durationMinutes += 24 * 60; // Если перелет через полночь
            }

            return durationMinutes;
        }
    }
}