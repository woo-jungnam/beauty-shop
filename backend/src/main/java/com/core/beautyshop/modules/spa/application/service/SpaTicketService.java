package com.core.beautyshop.modules.spa.application.service;

import com.core.beautyshop.modules.spa.application.dto.request.PurchasePackageRequest;
import com.core.beautyshop.modules.spa.application.dto.response.UserServiceTicketResponse;

import java.util.List;

public interface SpaTicketService {

    List<UserServiceTicketResponse> getMyTickets();

    List<UserServiceTicketResponse> getMyActiveTickets();

    UserServiceTicketResponse getTicketById(Long id);

    UserServiceTicketResponse purchasePackage(PurchasePackageRequest request);
}
