package com.kaif.reviewms.review.implementation;

import com.kaif.reviewms.review.Review;
import com.kaif.reviewms.review.ReviewRepository;
import com.kaif.reviewms.review.ReviewService;
import com.kaif.reviewms.review.dto.ReviewMessage;
import com.kaif.reviewms.review.dto.ReviewRequestDto;
import com.kaif.reviewms.review.dto.ReviewResponseDto;
import com.kaif.reviewms.review.exceptions.CompanyNotFoundException;
import com.kaif.reviewms.review.messaging.ReviewMessageProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import com.kaif.reviewms.review.clients.CompanyClient;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import feign.FeignException;

@Slf4j
@Service
public class ReviewServiceImplementation implements ReviewService {

    private final ReviewRepository reviewRepository;
    private CompanyClient companyClient;
    private ReviewMessageProducer reviewMessageProducer;

    public ReviewServiceImplementation(ReviewRepository reviewRepository, CompanyClient companyClient,ReviewMessageProducer reviewMessageProducer) {
        this.reviewRepository = reviewRepository;
        this.companyClient = companyClient;
        this.reviewMessageProducer = reviewMessageProducer;
    }

    @Override
    public List<ReviewResponseDto> getAllReviews(Long companyId) {
        log.info("Executing getAllReviews");
        List<Review> reviews;
        if (companyId != null) {
            log.info("Filtering by companyId: {}", companyId);
            reviews = reviewRepository.findByCompanyId(companyId);
        } else {
            log.info("Fetching all reviews");
            reviews = reviewRepository.findAll();
        }
        log.info("Found {} reviews", reviews.size());
        return reviews.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Override

    public boolean createReview(ReviewRequestDto reviewDto) {
        log.info("Attempting to create review for company: {}", reviewDto.getCompanyId());

        try {
            // Validate CompanyId using Feign
            companyClient.getCompany(reviewDto.getCompanyId());

        } catch (FeignException.NotFound e) { // <-- Catches 404
            log.warn("Company validation failed. Company not found with id: {}", reviewDto.getCompanyId());
            throw new CompanyNotFoundException("Company not found with id: " + reviewDto.getCompanyId());
        } catch (Exception e) {
            log.error("Error validating company id {}: {}", reviewDto.getCompanyId(), e.getMessage());
            throw new RuntimeException("Error communicating with company service", e);
        }

        // If validation passes, create the review
        Review review = mapToEntity(reviewDto);
        reviewRepository.save(review);
        ReviewMessage message = new ReviewMessage();
        message.setId(review.getId());
        message.setCompanyId(review.getCompanyId());
        message.setRating(review.getRating());
        message.setDescription(review.getDescription());

        reviewMessageProducer.sendMessage(message);
        log.info("Review created successfully with id: {}", review.getId());
        return true;
    }

    @Override
    public ReviewResponseDto getReviewById(Long reviewId) {
        log.info("Finding review {}", reviewId);
        Review review = reviewRepository.findById(reviewId).orElse(null);
        return (review != null) ? mapToDto(review) : null;
    }

    @Override
    public boolean updateReview(Long reviewId, ReviewRequestDto reviewDto) {
        log.info("Attempting to update review {}", reviewId);
        Optional<Review> reviewOpt = reviewRepository.findById(reviewId);
        if (reviewOpt.isEmpty()) {
            log.warn("Review not found: {}", reviewId);
            return false;
        }

        Review review = reviewOpt.get();

        // Check if the companyId is being changed, which should be allowed or validated
        if (!review.getCompanyId().equals(reviewDto.getCompanyId())) {
            log.warn("Review {} companyId changed from {} to {}. This may require re-validation.",
                    reviewId, review.getCompanyId(), reviewDto.getCompanyId());
        }

        review.setTitle(reviewDto.getTitle());
        review.setDescription(reviewDto.getDescription());
        review.setRating(reviewDto.getRating());
        review.setCompanyId(reviewDto.getCompanyId());

        reviewRepository.save(review);
        log.info("Review updated successfully: {}", reviewId);
        return true;
    }

    @Override
    public boolean deleteReview(Long reviewId) {
        log.info("Attempting to delete review {}", reviewId);
        Optional<Review> reviewOpt = reviewRepository.findById(reviewId);
        if (reviewOpt.isEmpty()) {
            log.warn("Review not found: {}", reviewId);
            return false;
        }

        reviewRepository.delete(reviewOpt.get());
        log.info("Review deleted successfully: {}", reviewId);
        return true;
    }

    @Override
    public Page<ReviewResponseDto> getReviewsPaginated(Long companyId, int page, int pageSize) {
        log.info("Finding reviews with pagination for company {} - page: {}, pageSize: {}", companyId, page, pageSize);
        Pageable pageable = PageRequest.of(page, pageSize);
        Page<Review> reviewPage = reviewRepository.findByCompanyId(companyId, pageable);
        return reviewPage.map(this::mapToDto);
    }

    @Override
    public List<ReviewResponseDto> getReviewsSorted(Long companyId, String field) {
        log.info("Finding reviews for company {} with sorting on field: {}", companyId, field);
        Sort sort = Sort.by(Sort.Direction.DESC, field);
        List<Review> reviews = reviewRepository.findByCompanyId(companyId, sort);
        return reviews.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<ReviewResponseDto> getReviewsByRatingGreaterThan(Long companyId, double minRating) {
        log.info("Finding reviews for company {} with rating greater than: {}", companyId, minRating);
        List<Review> reviews = reviewRepository.findByCompanyIdAndRatingGreaterThan(companyId, minRating);
        return reviews.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Override
    public Double getAverageRating(Long companyId) {
        log.info("Calculating average rating for company: {}", companyId);
        Double avgRating = reviewRepository.findAverageRatingByCompanyId(companyId);
        if (avgRating == null) {
            log.warn("No ratings found for company: {}", companyId);
            return 0.0;
        }
        log.info("Average rating for company {} is: {}", companyId, avgRating);
        return avgRating;
    }


    private ReviewResponseDto mapToDto(Review review) {
        ReviewResponseDto dto = new ReviewResponseDto();
        dto.setId(review.getId());
        dto.setTitle(review.getTitle());
        dto.setDescription(review.getDescription());
        dto.setRating(review.getRating());
        return dto;
    }

    private Review mapToEntity(ReviewRequestDto dto) {
        Review review = new Review();
        review.setTitle(dto.getTitle());
        review.setDescription(dto.getDescription());
        review.setRating(dto.getRating());
        review.setCompanyId(dto.getCompanyId());
        return review;
    }
}