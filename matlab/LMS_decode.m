function [decodedData] = LMS_decode(Ntx, Nrx, rx, training, mu)

% Compute transmission, training and data lengths
trainLength = size(training, 2);
transmitLength = size(rx, 2);
dataLength = transmitLength - trainLength;

% W is the MIMO decoder matrix at the receiver
W = eye(Nrx, Ntx);
decodedData = zeros(Ntx,dataLength);

% For each instant of time at the receiver
for time = 1:transmitLength
    xn = rx(:,time);
    
    rn = W*xn;
    yn = 1/sqrt(2)*(sign(real(rn)) + 1j*sign(imag(rn)));
    if (time <= trainLength)
        en = rn - training(:,time);
    else 
        en = rn - yn;
    end
    
    for k = 1:size(W,1)
         W(k,:) = W(k,:) - mu*en(k)*xn';
    end
    
    decodedData(:, time) = yn;
end

end