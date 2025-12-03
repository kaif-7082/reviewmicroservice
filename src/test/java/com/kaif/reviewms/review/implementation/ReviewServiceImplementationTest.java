package com.kaif.reviewms.review.implementation;

import com.kaif.reviewms.review.Review;
import com.kaif.reviewms.review.ReviewRepository;
import com.kaif.reviewms.review.clients.CompanyClient;
import com.kaif.reviewms.review.dto.ReviewRequestDto;
import com.kaif.reviewms.review.dto.ReviewResponseDto;
import com.kaif.reviewms.review.exceptions.CompanyNotFoundException;
import com.kaif.reviewms.review.messaging.ReviewMessageProducer;
import feign.FeignException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceImplementationTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private CompanyClient companyClient;

    @InjectMocks
    private ReviewServiceImplementation reviewService;

    @Mock
    private ReviewMessageProducer reviewMessageProducer;
    // --- TEST 1: Create Review (Success) ---
    @Test
    void testCreateReview_Success() {
        // 1. Arrange
        ReviewRequestDto request = new ReviewRequestDto();
        request.setCompanyId(1L);
        request.setTitle("Great Work");
        request.setDescription("Loved it");
        request.setRating(5.0);

        // Stub: Company Client returns successfully (void method)
        // For void methods, doNothing() is the default, but we can be explicit
        doNothing().when(companyClient).getCompany(1L);

        // Stub: Repository save
        Review savedReview = new Review();
        savedReview.setId(10L);
        when(reviewRepository.save(any(Review.class))).thenReturn(savedReview);

        // 2. Act
        boolean result = reviewService.createReview(request);

        // 3. Assert
        assertTrue(result);
        verify(companyClient, times(1)).getCompany(1L);
        verify(reviewRepository, times(1)).save(any(Review.class));
    }

    // --- TEST 2: Create Review (Company Not Found) ---
    @Test
    void testCreateReview_CompanyNotFound() {
        // 1. Arrange
        ReviewRequestDto request = new ReviewRequestDto();
        request.setCompanyId(99L);
        request.setTitle("Bad Review");

        // Stub: Throw 404 from Feign Client
        FeignException.NotFound notFound = mock(FeignException.NotFound.class);
        doThrow(notFound).when(companyClient).getCompany(99L);

        // 2. Act & Assert
        assertThrows(CompanyNotFoundException.class, () -> reviewService.createReview(request));

        // Verify we never saved to DB
        verify(reviewRepository, never()).save(any(Review.class));
    }

    // --- TEST 3: Get All Reviews (For a specific company) ---
    @Test
    void testGetAllReviews_ForCompany() {
        // 1. Arrange
        Long companyId = 1L;
        Review r1 = new Review();
        r1.setTitle("R1");
        r1.setCompanyId(companyId);

        Review r2 = new Review();
        r2.setTitle("R2");
        r2.setCompanyId(companyId);

        when(reviewRepository.findByCompanyId(companyId)).thenReturn(Arrays.asList(r1, r2));

        // 2. Act
        List<ReviewResponseDto> results = reviewService.getAllReviews(companyId);

        // 3. Assert
        assertEquals(2, results.size());
        assertEquals("R1", results.get(0).getTitle());
        verify(reviewRepository, times(1)).findByCompanyId(companyId);
    }

    // --- TEST 4: Get Review By ID (Found) ---
    @Test
    void testGetReviewById_Found() {
        // 1. Arrange
        Long reviewId = 5L;
        Review review = new Review();
        review.setId(reviewId);
        review.setTitle("Found Me");

        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(review));

        // 2. Act
        ReviewResponseDto result = reviewService.getReviewById(reviewId);

        // 3. Assert
        assertNotNull(result);
        assertEquals("Found Me", result.getTitle());
    }

    // --- TEST 5: Update Review (Success) ---
    @Test
    void testUpdateReview_Success() {
        // 1. Arrange
        Long reviewId = 5L;
        ReviewRequestDto updateRequest = new ReviewRequestDto();
        updateRequest.setTitle("New Title");
        updateRequest.setDescription("New Desc");
        updateRequest.setRating(4.0);
        updateRequest.setCompanyId(1L);

        Review existingReview = new Review();
        existingReview.setId(reviewId);
        existingReview.setTitle("Old Title");
        existingReview.setCompanyId(1L);

        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(existingReview));

        // 2. Act
        boolean result = reviewService.updateReview(reviewId, updateRequest);

        // 3. Assert
        assertTrue(result);
        assertEquals("New Title", existingReview.getTitle()); // Check if entity updated
        verify(reviewRepository, times(1)).save(existingReview);
    }

    // --- TEST 6: Delete Review (Success) ---
    @Test
    void testDeleteReview_Success() {
        // 1. Arrange
        Long reviewId = 5L;
        Review review = new Review();
        review.setId(reviewId);

        when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(review));

        // 2. Act
        boolean result = reviewService.deleteReview(reviewId);

        // 3. Assert
        assertTrue(result);
        verify(reviewRepository, times(1)).delete(review);
    }
}